Database Query - Admin Feature
Objective
Develop a new Database Query UI available only to Admin users. The feature will allow an Admin to enter and execute supported SQL statements and view the execution status directly on the screen.
UI Requirements
The Database Query screen must provide:
1.	A SQL category selector with the following options:
      o	DQL - Data Query Language: `SELECT`, `VALUES`
      o	DML - Data Manipulation Language: `INSERT`, `UPDATE`, `DELETE`, `REPLACE`
      o	DDL - Data Definition Language: `CREATE`, `DROP`, `ALTER`, `VACUUM`
      o	TCL - Transaction Control Language: `BEGIN`, `COMMIT`, `ROLLBACK`, `SAVEPOINT`
2.	A multi-line text area where the Admin can enter the SQL query.
3.	An Execute button to submit the query.
4.	A status/error area to display the execution result.
      Execution Behavior
      When the Admin clicks Execute:
1.	Validate that a SQL category is selected.
2.	Validate that the SQL query is not empty.
3.	Execute the submitted SQL query.
4.	If execution is successful, display:
      `Status: Success`
5.	If execution fails, display the database/query error on the same screen so the Admin can correct and retry the query.
      DQL Result Download
      For a successful DQL query (`SELECT` or `VALUES`):
1.	Retrieve the complete query result.
2.	Generate a CSV file from the result.
3.	The first row of the CSV must contain the result-set column names as headers.
4.	Add all returned data rows below the header.
5.	Download the generated CSV file to the user's browser.
6.	Display `Status: Success` on successful execution.
      DML, DDL, and TCL queries do not require CSV generation.
      Expected Flow
```text
Admin User
|
v
Open Database Query UI
|
v
Select DQL / DML / DDL / TCL
|
v
Enter SQL Query
|
v
Click Execute
|
v
Validate Input
|
+-- Invalid --> Display validation error
|
v
Execute SQL
|
+-- Failure --> Display execution error on screen
|
v
Success
|
+-- DQL ------> Generate CSV with headers --> Download CSV
|
+-- DML/DDL/TCL --> Display Success
```
Acceptance Criteria
•	Only an Admin user can access the Database Query UI.
•	The Admin can select DQL, DML, DDL, or TCL.
•	The Admin can enter SQL in a multi-line text box.
•	Empty queries must not be executed.
•	Successful execution displays Status: Success.
•	Failed execution displays the actual execution/database error on the same screen.
•	Successful DQL execution generates and downloads a CSV file.
•	The CSV must contain column headers followed by all returned result rows.
•	DML, DDL, and TCL execution does not generate a CSV.
Development Considerations
The implementation should also define appropriate controls for direct database access, including authorization, query validation, execution timeout, maximum result size, transaction handling, and audit logging. These controls should not change the functional behavior described above.

