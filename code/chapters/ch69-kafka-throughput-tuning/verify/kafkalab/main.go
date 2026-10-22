// Command kafkalab measures chapter 69's producer and consumer settings with a real Kafka client (franz-go's kgo)
// against an in-memory Kafka broker that speaks the Kafka protocol (franz-go's kfake), run as a separate process.
// Every setting is named after the Java client's property that does the same thing.
//
//	kafkalab broker -port 9092 -rtt 1ms     a broker that delays every response by the round trip (0: loopback)
//	kafkalab producer|consumer -broker host:port -broker-pid N
package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"hash/fnv"
	"math/rand"
	"net"
	"os"
	"os/signal"
	"sort"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	"github.com/twmb/franz-go/pkg/kfake"
	"github.com/twmb/franz-go/pkg/kgo"
	"github.com/twmb/franz-go/pkg/kmsg"
)

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: kafkalab broker|producer|consumer [flags]")
		os.Exit(2)
	}
	mode := os.Args[1]
	fs := flag.NewFlagSet(mode, flag.ExitOnError)
	port := fs.Int("port", 9092, "broker port")
	rtt := fs.Duration("rtt", 0, "added round-trip time per response")
	broker := fs.String("broker", "127.0.0.1:9092", "broker address")
	brokerPid := fs.Int("broker-pid", 0, "the broker's process, for its CPU time")
	fs.Parse(os.Args[2:])
	switch mode {
	case "broker":
		runBroker(*port, *rtt)
	case "producer":
		producer(*broker, *brokerPid)
	case "consumer":
		consumer(*broker)
	default:
		fmt.Fprintln(os.Stderr, "unknown mode", mode)
		os.Exit(2)
	}
}

// ---------------------------------------------------------------------------------------------------------- broker

func runBroker(port int, rtt time.Duration) {
	opts := []kfake.Opt{kfake.NumBrokers(1), kfake.Ports(port)}
	if rtt > 0 {
		opts = append(opts, kfake.ListenFn(func(network, address string) (net.Listener, error) {
			l, err := net.Listen(network, address)
			if err != nil {
				return nil, err
			}
			return &slowListener{Listener: l, delay: rtt}, nil
		}))
	}
	c, err := kfake.NewCluster(opts...)
	if err != nil {
		panic(err)
	}
	fmt.Println("broker", c.ListenAddrs(), "rtt", rtt)
	sig := make(chan os.Signal, 1)
	signal.Notify(sig, syscall.SIGTERM, os.Interrupt)
	<-sig
	c.Close()
}

// slowListener hands out connections whose responses arrive rtt later: a network round trip, without slowing the
// broker itself or limiting how many requests are in flight.
type slowListener struct {
	net.Listener
	delay time.Duration
}

func (l *slowListener) Accept() (net.Conn, error) {
	c, err := l.Listener.Accept()
	if err != nil {
		return nil, err
	}
	sc := &slowConn{Conn: c, delay: l.delay, out: make(chan chunk, 4096)}
	go sc.pump()
	return sc, nil
}

type chunk struct {
	due  time.Time
	data []byte
}

type slowConn struct {
	net.Conn
	delay time.Duration
	out   chan chunk
	once  sync.Once
}

func (c *slowConn) Write(b []byte) (int, error) {
	c.out <- chunk{time.Now().Add(c.delay), append([]byte(nil), b...)}
	return len(b), nil
}

func (c *slowConn) pump() {
	for ch := range c.out {
		if d := time.Until(ch.due); d > 0 {
			time.Sleep(d)
		}
		if _, err := c.Conn.Write(ch.data); err != nil {
			return
		}
	}
}

func (c *slowConn) Close() error {
	c.once.Do(func() { close(c.out) })
	return c.Conn.Close()
}

// ---------------------------------------------------------------------------------------------------------- events

type item struct {
	SKU   string  `json:"sku"`
	Qty   int     `json:"qty"`
	Price float64 `json:"price"`
}

type orderPlaced struct {
	Type         string    `json:"type"`
	OrderID      string    `json:"orderId"`
	RestaurantID string    `json:"restaurantId"`
	CustomerID   string    `json:"customerId"`
	Items        []item    `json:"items"`
	Total        float64   `json:"total"`
	Currency     string    `json:"currency"`
	City         string    `json:"city"`
	PlacedAt     time.Time `json:"placedAt"`
}

var dishes = []string{"paneer-tikka", "butter-chicken", "masala-dosa", "veg-biryani", "chole-bhature", "idli-sambar",
	"gulab-jamun", "dal-makhani", "hyderabadi-biryani", "pav-bhaji", "rogan-josh", "malai-kofta", "aloo-paratha", "filter-coffee"}
var cities = []string{"Bengaluru", "Mumbai", "Delhi", "Hyderabad", "Chennai", "Pune", "Kolkata"}

// events builds n OrderPlaced events of about 300 bytes of JSON each, keyed by order ID.
func events(n int) [][2][]byte {
	r := rand.New(rand.NewSource(42))
	base := time.Date(2026, 1, 26, 19, 0, 0, 0, time.UTC)
	out := make([][2][]byte, n)
	for i := range out {
		o := orderPlaced{Type: "OrderPlaced", OrderID: fmt.Sprintf("ord-%08d", i), RestaurantID: fmt.Sprintf("rest-%04d", r.Intn(2000)),
			CustomerID: fmt.Sprintf("cust-%06d", r.Intn(500000)), Currency: "INR", City: cities[r.Intn(len(cities))],
			PlacedAt: base.Add(time.Duration(i) * 3 * time.Millisecond)}
		for k := 0; k < 1+r.Intn(4); k++ {
			it := item{SKU: dishes[r.Intn(len(dishes))], Qty: 1 + r.Intn(3), Price: float64(90+r.Intn(400)) + 0.5}
			o.Items = append(o.Items, it)
			o.Total += float64(it.Qty) * it.Price
		}
		v, _ := json.Marshal(o)
		out[i] = [2][]byte{[]byte(o.OrderID), v}
	}
	return out
}

// ---------------------------------------------------------------------------------------------------------- helpers

func createTopic(cl *kgo.Client, topic string, partitions int32) {
	req := kmsg.NewPtrCreateTopicsRequest()
	t := kmsg.NewCreateTopicsRequestTopic()
	t.Topic = topic
	t.NumPartitions = partitions
	t.ReplicationFactor = 1
	req.Topics = append(req.Topics, t)
	if _, err := req.RequestWith(context.Background(), cl); err != nil {
		panic(err)
	}
}

// cpuSeconds of a process from /proc (user + system); this process if pid is 0.
func cpuSeconds(pid int) float64 {
	p := "self"
	if pid > 0 {
		p = strconv.Itoa(pid)
	}
	b, err := os.ReadFile("/proc/" + p + "/stat")
	if err != nil {
		return 0
	}
	f := strings.Fields(string(b[strings.LastIndexByte(string(b), ')')+2:]))
	ut, _ := strconv.ParseFloat(f[11], 64)
	st, _ := strconv.ParseFloat(f[12], 64)
	return (ut + st) / 100
}

func pct(sorted []time.Duration, p float64) float64 {
	if len(sorted) == 0 {
		return 0
	}
	i := int(float64(len(sorted))*p/100+0.999999) - 1
	if i < 0 {
		i = 0
	}
	if i >= len(sorted) {
		i = len(sorted) - 1
	}
	return float64(sorted[i].Microseconds()) / 1000
}

type batchStats struct {
	batches, records, uncompressed, compressed, requests atomic.Int64
}

func (s *batchStats) OnProduceBatchWritten(_ kgo.BrokerMetadata, _ string, _ int32, m kgo.ProduceBatchMetrics) {
	s.batches.Add(1)
	s.records.Add(int64(m.NumRecords))
	s.uncompressed.Add(int64(m.UncompressedBytes))
	s.compressed.Add(int64(m.CompressedBytes))
}

func (s *batchStats) OnBrokerWrite(_ kgo.BrokerMetadata, key int16, _ int, _, _ time.Duration, err error) {
	if key == 0 && err == nil { // Produce
		s.requests.Add(1)
	}
}

// ---------------------------------------------------------------------------------------------------------- producer

type producerConfig struct {
	label       string
	lingerMs    int
	batchSize   int32
	compression kgo.CompressionCodec
	inFlight    int
}

func (c producerConfig) opts(broker string, stats *batchStats) []kgo.Opt {
	opts := []kgo.Opt{kgo.SeedBrokers(broker), kgo.ProducerLinger(time.Duration(c.lingerMs) * time.Millisecond), kgo.ProducerBatchMaxBytes(c.batchSize),
		kgo.ProducerBatchCompression(c.compression), kgo.RequiredAcks(kgo.AllISRAcks()), kgo.MaxBufferedRecords(100_000), kgo.WithHooks(stats)}
	if c.inFlight == 1 {
		// idempotent producers keep 5 requests in flight (as the Java client's default); one at a time needs it off here
		opts = append(opts, kgo.DisableIdempotentWrite(), kgo.MaxProduceRequestsInflightPerBroker(1))
	}
	return opts
}

var producerConfigs = []producerConfig{
	{"linger.ms=0, batch.size=16384 (Kafka 3.x defaults)", 0, 16384, kgo.NoCompression(), 5},
	{"linger.ms=5, batch.size=16384 (Kafka 4.x defaults)", 5, 16384, kgo.NoCompression(), 5},
	{"linger.ms=20, batch.size=131072", 20, 131072, kgo.NoCompression(), 5},
	{"linger.ms=20, batch.size=131072, lz4", 20, 131072, kgo.Lz4Compression(), 5},
	{"linger.ms=20, batch.size=131072, zstd", 20, 131072, kgo.ZstdCompression(), 5},
	{"linger.ms=20, batch.size=131072, snappy", 20, 131072, kgo.SnappyCompression(), 5},
	{"linger.ms=20, batch.size=131072, gzip", 20, 131072, kgo.GzipCompression(), 5},
	{"linger.ms=0, max.in.flight=1, idempotence off", 0, 16384, kgo.NoCompression(), 1},
}

func producer(broker string, brokerPid int) {
	evs := events(300_000)
	var bytesTotal int
	for _, e := range evs {
		bytesTotal += len(e[1])
	}
	fmt.Printf("300,000 OrderPlaced events, %d bytes of JSON on average, keyed by order ID, to a topic of 12 partitions; acks=all, idempotent (5 requests in flight) unless shown\n", bytesTotal/len(evs))
	fmt.Println("as fast as the producer can send (100,000 records buffered at most):")
	fmt.Printf("%-50s %10s %8s %12s %10s %9s %10s %10s\n", "settings", "records/s", "MB/s", "records/req", "requests", "ratio", "client CPU", "broker CPU")
	for i, c := range producerConfigs {
		stats := &batchStats{}
		cl, err := kgo.NewClient(c.opts(broker, stats)...)
		if err != nil {
			panic(err)
		}
		topic := fmt.Sprintf("orders-max-%d-%d", i, time.Now().UnixNano())
		createTopic(cl, topic, 12)
		warm(cl, topic, evs[:5000])
		stats.records.Store(0)
		stats.batches.Store(0)
		stats.requests.Store(0)
		stats.uncompressed.Store(0)
		stats.compressed.Store(0)
		c0, b0 := cpuSeconds(0), cpuSeconds(brokerPid)
		t0 := time.Now()
		var wg sync.WaitGroup
		wg.Add(len(evs))
		for _, e := range evs {
			cl.Produce(context.Background(), &kgo.Record{Topic: topic, Key: e[0], Value: e[1]}, func(_ *kgo.Record, err error) {
				if err != nil {
					panic(err)
				}
				wg.Done()
			})
		}
		wg.Wait()
		el := time.Since(t0).Seconds()
		c1, b1 := cpuSeconds(0), cpuSeconds(brokerPid)
		fmt.Printf("%-50s %10.0f %8.1f %12.1f %10d %8.2fx %9.2fs %9.2fs\n", c.label, float64(len(evs))/el, float64(bytesTotal)/el/1e6,
			float64(stats.records.Load())/float64(stats.requests.Load()), stats.requests.Load(), float64(stats.uncompressed.Load())/float64(stats.compressed.Load()), c1-c0, b1-b0)
		cl.Close()
	}

	fmt.Println()
	fmt.Println("at a steady 10,000 records/s for 10 s; latency from when each record was due until the broker acknowledged it:")
	fmt.Printf("%-50s %10s %10s %10s %12s %10s %10s\n", "settings", "p50 ms", "p99 ms", "max ms", "records/req", "requests", "broker CPU")
	for i, c := range producerConfigs[:3] {
		stats := &batchStats{}
		cl, err := kgo.NewClient(c.opts(broker, stats)...)
		if err != nil {
			panic(err)
		}
		topic := fmt.Sprintf("orders-rate-%d-%d", i, time.Now().UnixNano())
		createTopic(cl, topic, 12)
		warm(cl, topic, evs[:5000])
		stats.records.Store(0)
		stats.requests.Store(0)
		const rate, n = 10_000, 100_000
		lat := make([]time.Duration, n)
		var wg sync.WaitGroup
		wg.Add(n)
		b0 := cpuSeconds(brokerPid)
		t0 := time.Now()
		for k := 0; k < n; k++ {
			due := t0.Add(time.Duration(k) * time.Second / rate)
			if d := time.Until(due); d > 0 {
				time.Sleep(d)
			}
			k := k
			e := evs[k%len(evs)]
			cl.Produce(context.Background(), &kgo.Record{Topic: topic, Key: e[0], Value: e[1]}, func(_ *kgo.Record, err error) {
				if err != nil {
					panic(err)
				}
				lat[k] = time.Since(due)
				wg.Done()
			})
		}
		wg.Wait()
		b1 := cpuSeconds(brokerPid)
		sort.Slice(lat, func(a, b int) bool { return lat[a] < lat[b] })
		fmt.Printf("%-50s %10.2f %10.2f %10.2f %12.1f %10d %9.2fs\n", c.label, pct(lat, 50), pct(lat, 99), pct(lat, 100),
			float64(stats.records.Load())/float64(stats.requests.Load()), stats.requests.Load(), b1-b0)
		cl.Close()
	}
}

func warm(cl *kgo.Client, topic string, evs [][2][]byte) {
	var wg sync.WaitGroup
	wg.Add(len(evs))
	for _, e := range evs {
		cl.Produce(context.Background(), &kgo.Record{Topic: topic, Key: e[0], Value: e[1]}, func(_ *kgo.Record, err error) { wg.Done() })
	}
	wg.Wait()
}

// ---------------------------------------------------------------------------------------------------------- consumer

// producerFor returns a client for writing test data quickly.
func producerFor(broker string) *kgo.Client {
	cl, err := kgo.NewClient(kgo.SeedBrokers(broker), kgo.ProducerLinger(20*time.Millisecond), kgo.ProducerBatchMaxBytes(1<<20), kgo.MaxBufferedRecords(100_000))
	if err != nil {
		panic(err)
	}
	return cl
}

type consumerConfig struct {
	label      string
	partitions int32
	consumers  int
	mode       string // "record": 1 ms per record; "keyed": 1 ms per record, 32 at a time by key; "batch": one 5 ms write per poll
}

func consumer(broker string) {
	evs := events(12_000)
	fmt.Println("12,000 OrderPlaced events produced at once into a topic the group already consumes; each consumer is a member of one group; handling a record means a 1 ms write")
	fmt.Println("(a database round trip); 'batch' writes a whole poll (max.poll.records=500) in one 5 ms write instead")
	fmt.Printf("%-62s %10s %10s %12s %14s\n", "set-up", "partitions", "consumers", "records/s", "idle consumers")
	configs := []consumerConfig{
		{"one record at a time", 6, 1, "record"},
		{"one record at a time", 6, 3, "record"},
		{"one record at a time", 6, 6, "record"},
		{"one record at a time", 6, 12, "record"},
		{"in parallel by key, 32 at a time, inside one consumer", 6, 1, "keyed"},
		{"a whole poll in one write", 6, 1, "batch"},
		{"a whole poll in one write", 6, 6, "batch"},
	}
	for i, c := range configs {
		topic := fmt.Sprintf("orders-c-%d-%d", i, time.Now().UnixNano())
		writer := producerFor(broker)
		createTopic(writer, topic, c.partitions)
		group := topic + "-group"
		var done atomic.Int64
		var idle atomic.Int64
		var assigned atomic.Int64
		var wg sync.WaitGroup
		ctx, cancel := context.WithCancel(context.Background())
		for m := 0; m < c.consumers; m++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				got := member(ctx, broker, topic, group, c.mode, int64(len(evs)), &done, &assigned)
				if got == 0 {
					idle.Add(1)
				}
			}()
		}
		// let the group settle (every member joined, partitions assigned) before the events arrive
		for k := 0; k < 300 && assigned.Load() < int64(min(c.consumers, int(c.partitions))); k++ {
			time.Sleep(50 * time.Millisecond)
		}
		time.Sleep(time.Second)
		t0 := time.Now()
		warm(writer, topic, evs)
		writer.Close()
		for done.Load() < int64(len(evs)) {
			time.Sleep(5 * time.Millisecond)
		}
		el := time.Since(t0).Seconds()
		cancel()
		wg.Wait()
		fmt.Printf("%-62s %10d %10d %12.0f %14d\n", c.label, c.partitions, c.consumers, float64(len(evs))/el, idle.Load())
	}
}

// member consumes until the context ends; returns how many records it handled.
func member(ctx context.Context, broker, topic, group, mode string, total int64, done, assigned *atomic.Int64) int64 {
	cl, err := kgo.NewClient(kgo.SeedBrokers(broker), kgo.ConsumerGroup(group), kgo.ConsumeTopics(topic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()), kgo.FetchMaxWait(100*time.Millisecond),
		kgo.OnPartitionsAssigned(func(_ context.Context, _ *kgo.Client, m map[string][]int32) {
			if len(m[topic]) > 0 {
				assigned.Add(1)
			}
		}))
	if err != nil {
		panic(err)
	}
	defer cl.Close()
	var mine int64
	for ctx.Err() == nil {
		fetches := cl.PollRecords(ctx, 500)
		recs := fetches.Records()
		if len(recs) == 0 {
			continue
		}
		switch mode {
		case "record":
			for range recs {
				time.Sleep(time.Millisecond)
			}
		case "batch":
			time.Sleep(5 * time.Millisecond)
		case "keyed":
			// records with the same key stay in order on the same worker; different keys run side by side
			var wg sync.WaitGroup
			lanes := make([][]*kgo.Record, 32)
			for _, r := range recs {
				h := fnv.New32a()
				h.Write(r.Key)
				lanes[h.Sum32()%32] = append(lanes[h.Sum32()%32], r)
			}
			for _, lane := range lanes {
				if len(lane) == 0 {
					continue
				}
				wg.Add(1)
				go func(lane []*kgo.Record) {
					defer wg.Done()
					for range lane {
						time.Sleep(time.Millisecond)
					}
				}(lane)
			}
			wg.Wait()
		}
		mine += int64(len(recs))
		done.Add(int64(len(recs)))
		if done.Load() >= total {
			break
		}
	}
	return mine
}
