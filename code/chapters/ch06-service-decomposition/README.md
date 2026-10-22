# Chapter 6 code — Service Decomposition, measured

Docs: `docs/foundations/ch06-service-decomposition.md`

Instead of arguing about service boundaries, score them:

| Class | What it does |
|---|---|
| `DomainModel` | Capabilities, owning teams, and use-case interactions (sync/async, calls per execution) |
| `FoodFleetModel` | FoodFleet's 14 capabilities, 6 teams, 2 use cases, and 4 candidate decompositions |
| `DecompositionEvaluator` | Sync hops and availability per use case, cohesion, teams per service, services per team |
| `CoChangeAnalyzer` | Mines a commit log for modules that change together and suggests groups |
| `GitLogImporter` | Turns real `git log --name-only` output plus path→module rules into module-level commits, dropping mass changes |
| `DecompositionReport` | Prints the comparison |

```bash
cd code
mvn -B -pl chapters/ch06-service-decomposition -am verify
mvn -q -pl chapters/ch06-service-decomposition spring-boot:run
```

Output (verified):

```text
Decomposition                          Services  Sync hops   Async Availability   Cohesion  Max teams/ Max services/
A. One service per entity                    14         16       3       98.91%         0%           1            4
B. By business capability                     9          9       3       99.40%        58%           1            2
C. Three coarse services                      3          6       3       99.70%        68%           3            2
D. Modular monolith (one deployable)          1          0       0       99.90%       100%           6            1
```

Use your own monolith's history:

```bash
# 1. export history (one '#<hash>' line per commit, then the files it changed)
git -C ~/work/monolith log --since=12.months --no-merges --name-only --pretty=format:'#%h' > /tmp/git.log

# 2. map path prefixes to modules (longest prefix wins; unmatched paths are ignored)
cat > /tmp/modules.txt <<'MAP'
src/main/java/com/acme/order/=order
src/main/java/com/acme/order/line/=order-line
src/main/java/com/acme/cart/=cart
src/main/resources/db/migration/order/=order
MAP

# 3. analyse (commits touching more than 6 modules are skipped as mass changes)
mvn -q -pl chapters/ch06-service-decomposition spring-boot:run \
  -Dspring-boot.run.arguments="--git-log=/tmp/git.log --mapping=/tmp/modules.txt --max-modules-per-commit=6"
```
