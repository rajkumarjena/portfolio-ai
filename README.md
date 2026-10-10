Initial Design

com.artech.portfolio/
├── bond/
│    ├── model/          <-- Bond entity
│    ├── repository/     <-- BondRepository
│    ├── service/        <-- BondService
│    └── controller/     <-- BondController
├── fd/
│    ├── model/          <-- FD entity
│    ├── repository/     <-- FDRepository
│    ├── service/        <-- FDService
│    └── controller/     <-- FDController
├── share/
│    ├── model/          <-- Share entity
│    ├── repository/     <-- ShareRepository
│    ├── service/        <-- ShareService
│    └── controller/     <-- ShareController
├── mutualfund/
│    ├── model/          <-- MutualFund entity
│    ├── repository/     <-- MutualFundRepository
│    ├── service/        <-- MutualFundService
│    └── controller/     <-- MutualFundController
├── portfolio/
│    ├── service/        <-- PortfolioService (aggregates all modules)
│    └── controller/     <-- PortfolioController (summary endpoints)
└── BondTrackerApplication.java




Overall Design

portfolio-app/
├── backend/ (Spring Boot service code)
│    └── src/main/java/com/artech/portfolio/
│         ├── model/          <-- JPA entities (Bond.java)
│         ├── repository/     <-- Spring Data repositories
│         ├── service/        <-- Business logic
│         ├── controller/     <-- REST controllers
│         └── BondTrackerApplication.java
│    └── src/main/resources/
│         ├── application.yaml
│         └── static/         <-- Angular build output goes here
│              ├── index.html
│              ├── main.js
│              ├── styles.css
│              └── assets/
├── frontend/ (Angular source code, optional if you keep it separate)
│    └── src/app/bond-form/
│         ├── bond-form.component.html
│         ├── bond-form.component.ts
│         ├── bond-form.component.css
│         └── bond-form.component.spec.ts
└── pom.xml
