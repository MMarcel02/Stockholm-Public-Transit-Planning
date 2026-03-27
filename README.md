## Instructions to run and test routing engine:

1) First: 
mvn clean compile

2) You can check test data inside test1.jsonl

3) Run:

To run with output in the terminal do:
mvn -q exec:java < test1.jsonl

To run with saving the input to test1_results.jsonl do:
mvn -q exec:java < test1.jsonl > test1_results.jsonl

To test the stockholm random routes use:
mvn -q exec:java < stockholm_routes.jsonl > stockholm_routes_results.jsonl