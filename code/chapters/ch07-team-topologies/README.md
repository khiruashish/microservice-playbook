# Chapter 7 code — Conway's Law & Team Topologies, checked

Docs: `docs/foundations/ch07-conways-law-team-topologies.md`

The organisation design is a model in version control, and every claim about it is checked:

| Class | What it does |
|---|---|
| `topology/Topology` | Teams (type, size), domains (complexity), components (owner, data owner, path), dependencies, interaction modes |
| `topology/FoodFleetTeams` | FoodFleet in 2023 (layer teams, shared database) and 2026 (Team Topologies), plus one quarter's roadmap |
| `analysis/FlowAnalyzer` | Teams and handoffs per feature |
| `analysis/CognitiveLoadAnalyzer` | Domain-complexity load per team, team-size limits |
| `analysis/ConwayAnalyzer` | Socio-technical congruence: coordination needs vs declared interactions; interaction-mode warnings |
| `ownership/CodeOwners` | Generates GitHub `CODEOWNERS` from the topology, or checks an existing one (last match wins) |
| `ownership/BackstageCatalog` | Generates Backstage `Group` and `Component` entities |

```bash
cd code
mvn -B -pl chapters/ch07-team-topologies -am verify
mvn -q -pl chapters/ch07-team-topologies spring-boot:run                                   # 2023 vs 2026 report
mvn -q -pl chapters/ch07-team-topologies spring-boot:run -Dspring-boot.run.arguments=--codeowners=generate
mvn -q -pl chapters/ch07-team-topologies spring-boot:run -Dspring-boot.run.arguments=--codeowners=$PWD/CODEOWNERS
mvn -q -pl chapters/ch07-team-topologies spring-boot:run -Dspring-boot.run.arguments=--backstage
```

Headline numbers (verified):

| | 2023 layer teams | 2026 Team Topologies |
|---|---:|---:|
| Teams per feature (avg of 8) | 3.38 | 1.75 |
| Handoffs | 19 | 6 |
| Single-team features | 0 of 8 | 2 of 8 |
| Teams over cognitive-load budget | 4 of 6 | 1 of 8 (Platform) |
| Socio-technical congruence | 62% (8/13) | 92% (12/13) |
| Collaborations older than 6 months | 4 | 1 |
