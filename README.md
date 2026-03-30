## Instructions to Run and Test Routing Engine

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
mvn -q exec:java < stockholm_routes.jsonl > stockholm_routes_results.jsonl
```