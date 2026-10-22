// Runs FoodFleet API requests through Coraza with the OWASP Core Rule Set at each paranoia level and reports
// what was blocked: benign requests (false positives), attacks (detections), and the time per request.
//
//	go run . -crs <path to coreruleset checkout> [-corpus corpus.jsonl] [-extra api-setup.conf,exclusions.conf]
//
// Each line of the corpus is {"kind":"benign"|"attack"|"logic","name":...,"method":...,"uri":...,
// "headers":{...},"body":...}. "logic" requests are attacks on the application's rules (mass assignment,
// negative quantities, another tenant's id) that look like ordinary traffic.
package main

import (
	"bufio"
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/corazawaf/coraza/v3"
	"github.com/corazawaf/coraza/v3/types"
)

type request struct {
	Kind    string            `json:"kind"`
	Name    string            `json:"name"`
	Method  string            `json:"method"`
	URI     string            `json:"uri"`
	Headers map[string]string `json:"headers"`
	Body    string            `json:"body"`
}

// The engine settings ModSecurity's and Coraza's recommended configuration ship with: inspect bodies, parse JSON.
const engine = `
SecRuleEngine On
SecRequestBodyAccess On
SecRequestBodyLimit 13107200
SecRequestBodyNoFilesLimit 131072
SecRequestBodyLimitAction Reject
SecRule REQUEST_HEADERS:Content-Type "^application/json" \
    "id:200001,phase:1,t:none,t:lowercase,pass,nolog,ctl:requestBodyProcessor=JSON"
SecRule REQBODY_ERROR "!@eq 0" \
    "id:200002,phase:2,t:none,log,deny,status:400,msg:'Failed to parse request body.'"
`

func newWAF(crs string, paranoia int, extra []string) (coraza.WAF, error) {
	directives := engine +
		fmt.Sprintf("SecAction \"id:900000,phase:1,pass,t:none,nolog,setvar:tx.blocking_paranoia_level=%d\"\n", paranoia) +
		"Include " + filepath.Join(crs, "crs-setup.conf.example") + "\n"
	for _, f := range extra {
		directives += "Include " + f + "\n"
	}
	directives += "Include " + filepath.Join(crs, "rules", "*.conf") + "\n"
	return coraza.NewWAF(coraza.NewWAFConfig().WithDirectives(directives))
}

type result struct {
	blocked bool
	rules   []string
	elapsed time.Duration
}

func evaluate(waf coraza.WAF, r request) result {
	start := time.Now()
	tx := waf.NewTransaction()
	defer tx.Close()
	tx.ProcessConnection("203.0.113.10", 51234, "10.0.0.5", 8080)
	tx.ProcessURI(r.URI, r.Method, "HTTP/1.1")
	tx.AddRequestHeader("Host", "api.foodfleet.in")
	// what every real client sends; a request in the corpus can override them
	defaults := map[string]string{
		"User-Agent":      "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36",
		"Accept":          "application/json",
		"Accept-Language": "en-IN,en;q=0.9",
	}
	for k, v := range defaults {
		if _, ok := r.Headers[k]; !ok {
			tx.AddRequestHeader(k, v)
		}
	}
	for k, v := range r.Headers {
		tx.AddRequestHeader(k, v)
	}
	if r.Body != "" {
		tx.AddRequestHeader("Content-Length", fmt.Sprint(len(r.Body)))
	}
	var it *types.Interruption
	if it = tx.ProcessRequestHeaders(); it == nil {
		if r.Body != "" {
			if _, _, err := tx.WriteRequestBody([]byte(r.Body)); err != nil {
				panic(err)
			}
		}
		var err error
		if it, err = tx.ProcessRequestBody(); err != nil {
			panic(err)
		}
	}
	elapsed := time.Since(start)
	var rules []string
	for _, m := range tx.MatchedRules() {
		id := m.Rule().ID()
		// the detection rules (911xxx-944xxx with a message); skips, markers and helpers have no message
		if id >= 911100 && id < 949000 && id%1000 >= 100 && m.Message() != "" {
			rules = append(rules, fmt.Sprint(id))
		}
	}
	tx.ProcessLogging()
	return result{blocked: it != nil, rules: rules, elapsed: elapsed}
}

func main() {
	crs := flag.String("crs", "", "path to a coreruleset checkout")
	corpusPath := flag.String("corpus", "corpus.jsonl", "requests to evaluate")
	extraFiles := flag.String("extra", "", "comma-separated files to load after crs-setup and before the CRS rules")
	verbose := flag.Bool("v", false, "list every blocked benign request and missed attack")
	flag.Parse()

	f, err := os.Open(*corpusPath)
	if err != nil {
		panic(err)
	}
	var corpus []request
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 1<<20), 1<<20)
	for sc.Scan() {
		line := strings.TrimSpace(sc.Text())
		if line == "" || strings.HasPrefix(line, "//") {
			continue
		}
		var r request
		if err := json.Unmarshal([]byte(line), &r); err != nil {
			panic(fmt.Sprintf("%s: %v", line, err))
		}
		corpus = append(corpus, r)
	}
	counts := map[string]int{}
	for _, r := range corpus {
		counts[r.Kind]++
	}
	fmt.Printf("corpus: %d benign, %d attacks, %d application-logic abuses\n", counts["benign"], counts["attack"], counts["logic"])
	fmt.Printf("%-10s %-22s %-20s %-24s %s\n", "paranoia", "benign blocked", "attacks blocked", "logic abuses blocked", "median / p99 per request")
	for pl := 1; pl <= 4; pl++ {
		var extra []string
		if *extraFiles != "" {
			extra = strings.Split(*extraFiles, ",")
		}
		waf, err := newWAF(*crs, pl, extra)
		if err != nil {
			panic(err)
		}
		blocked := map[string]int{}
		var times []time.Duration
		var falsePositives, misses []string
		for round := 0; round < 20; round++ {
			for _, r := range corpus {
				res := evaluate(waf, r)
				times = append(times, res.elapsed)
				if round > 0 {
					continue
				}
				if res.blocked {
					blocked[r.Kind]++
					if r.Kind == "benign" {
						falsePositives = append(falsePositives, r.Name+" ("+strings.Join(res.rules, ",")+")")
					}
				} else if r.Kind == "attack" {
					misses = append(misses, r.Name)
				}
			}
		}
		sort.Slice(times, func(i, j int) bool { return times[i] < times[j] })
		fmt.Printf("PL%-8d %-22s %-20s %-24s %v / %v\n", pl,
			fmt.Sprintf("%d of %d", blocked["benign"], counts["benign"]),
			fmt.Sprintf("%d of %d", blocked["attack"], counts["attack"]),
			fmt.Sprintf("%d of %d", blocked["logic"], counts["logic"]),
			times[len(times)/2].Round(time.Microsecond), times[len(times)*99/100].Round(time.Microsecond))
		if *verbose {
			for _, n := range falsePositives {
				fmt.Println("    false positive:", n)
			}
			for _, n := range misses {
				fmt.Println("    missed attack: ", n)
			}
		}
	}
}
