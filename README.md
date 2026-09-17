
# Year 1 Project 2

Research goal: create analysis tool for identifying best public transit routes to cut

# Stockholm Public Transit RAPTOR Implementation

## To run GUI launch from src/main/java/team18/App.java

## For testing generic routing engine look below
## Instructions to Run and Test Routing Engine

### 0. Make sure data is in correct place
Make sure data in the load string matches the place where it actually is
For Stockholm we can put into data/stockholm/
For midway they just have it in the root

### 1. Build the Project
Before running the engine, ensure the project is compiled:
```bash
mvn clean compile
```

### 2. View Test Data
You can inspect the sample input data here:
* `test1.jsonl`
* `stockholm_routes.jsonl`

### 3. Execution Commands

#### Run in Terminal
To process the test data and view the output directly in your console:
```bash
mvn -q exec:java < test1.jsonl
```

#### Run and Save Output
To process the test data and save the results to a file:
```bash
mvn -q exec:java < test1.jsonl > test1_results.jsonl
```

#### Test Stockholm Random Routes
To run the specific Stockholm dataset and save the results:
```bash
mvn -q exec:java < stockholm_inner_urban_routes.jsonl > stockholm_inner_urban_routes_results.jsonl
```
