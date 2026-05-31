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
mvn -q exec:java < stockholm_inner_urban_routes.jsonl > stockholm_inner_urban_routes_results.jsonl
```
TO DO:

MINIMUM: INPUT BOXES WORK + STRAIGHT LINE PATHS FOR EACH ROUTESTEP IN A JOURNEY 

MISC:
*   REPORT
*   SLIDES

FRONTEND:
*   DISPLAY LARGER MAP + DISPLAY IT OFFLINE (ONLY ONLINE NOW)

*   DRAW POINT
    DRAW LINE
    DRAW ROUTE (STRAIGHT LINE BETWEEN 2 STOPS) OPTIONAL: PARSE SHAPES.TXT INTO ROUTEPATHS + DISPLAY ROUTES ACCURATELY
    IF CLICK ON STOP POINT, GIVE INFO LIKE COORDINATES, NAME OPTIONAL: HIGHLIGHT ROUTES PASSING THROUGH IT


*   GET INPUT BOX TO PASS COORDINATES AND A START TIME TO ALGORITHM (JUST BY TYPING FOR NOW)
    CATCH ALGORITHM RESULT AND SPIT IT OUT IN TEXT
    DISPLAY ALGORITHM RESULT NICER (MAYBE LIKE SERIES OF BOXES ON THE SIDE GOOGLE MAPS STYLE)
    DISPLAY ALGORITHM RESULT ON MAP

    PASS COORDINATES TO INPUT BOX USING MAP CLICKS RATHER THAN JUST TEXT (MAYBE LIKE ORGANIC MAP APP)
    
    OPTIONAL: MAKE SURE MAP IS FAST
    OPTIONAL: ZOOMING DYNAMICALLY (DRAWINGS RESIZE BASED ON ZOOM LEVEL)
    OPTIONAL: ADD A CALENDAR DATE PICKER, AND PASS THAT TO ALGORITHM TOO

BACKEND:
*   DESIGN SOME TESTS FOR ROUTINGENGINE
*   CHECK PARSER ON OTHER DATASETS AND FIX ANY ISSUES
    MAKE RAPTOR OUTPUT IN A FORMAT MORE FRIENDLY TO FRONTEND
*   MAKE ROUTER INTERFACE/STRATEGY PATTERN
*   FINISH A*
    WRITE COMPARISON MECHANISM FOR A* AND RAPTOR

    OPTIONAL: MAKE AUTOMATIC TEST GENERATOR FOR ROUTING
    OPTIONAL: IMPLEMENT ALGORITHM WITH CALENDAR AND CALENDAR DATES