1.  i  executed below delete queries under 'Database Query' -> 'Database Query' and passed below delete statements under multi input text box.
2. 	DELETE FROM mf_amc where 1 = 1;
      DELETE FROM mf_folio_scheme_transactions where 1 = 1;
      DELETE FROM mf_folio_schemes where 1 = 1;
      DELETE FROM mf_folio_schemes_nominees where 1 = 1;
      DELETE FROM mf_folio_schemes_valuation where 1 = 1;
      DELETE FROM mf_folios where 1 = 1;
      DELETE FROM mf_import_batch where 1 = 1;
      DELETE FROM mf_portfolio_summary where 1 = 1;
      DELETE FROM mf_summary_reconciliation where 1 = 1;
      DELETE FROM mf_tmp_folio_scheme_transactions where 1 = 1;
      DELETE FROM mf_tmp_folio_schemes where 1 = 1;
      DELETE FROM mf_tmp_folio_schemes_nominees where 1 = 1;
      DELETE FROM mf_tmp_folio_schemes_valuation where 1 = 1;
      DELETE FROM mf_tmp_folios where 1 = 1;
      DELETE FROM mf_tmp_portfolio_summary where 1 = 1;
3. when i clicked the 'Execute', it shows in screen how many records it deleted from each table , its ok.
4. i again clicked the 'Execute' button on same time, it gave me error as 'Error: Query failed: [SQLITE_ERROR] SQL error or missing database (cannot commit - no transaction is active)', please fix this part, on next click it should show zero records deleted as on first click it deleted all recordds.
5. i went to Dashboard, widget is showing bad behaviour 'Summary unavailable', rather it should show proper data for Bonds and all zero data for Mutual Fund widget as we deleted all table data for widget in above step, please fix this part.
6. Also, i got below error in applicationn, may be database getting stuck or some issue for which above point-4 and point-5 is impact for point 1 till point 3, so please fix the application error issue, the applicationn error as below
   "C:\Program Files\Amazon Corretto\jdk21.0.8_9\bin\java.exe" -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:63768,suspend=y,server=n -javaagent:C:\Users\rajku\AppData\Local\JetBrains\IdeaIC2025.2\captureAgent\debugger-agent.jar=file:///C:/Users/rajku/AppData/Local/Temp/capture17155479476442737067.props -Dkotlinx.coroutines.debug.enable.creation.stack.trace=false -Ddebugger.agent.enable.coroutines=true -Dkotlinx.coroutines.debug.enable.flows.stack.trace=true -Dkotlinx.coroutines.debug.enable.mutable.state.flows.stack.trace=true -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -classpath "C:\Drive-D\investment\AI\portfolio-ai\target\classes;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-web\4.1.1\spring-boot-starter-web-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-jackson\4.1.1\spring-boot-starter-jackson-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-jackson\4.1.1\spring-boot-jackson-4.1.1.jar;C:\Users\rajku\.m2\repository\tools\jackson\core\jackson-databind\3.1.5\jackson-databind-3.1.5.jar;C:\Users\rajku\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.21\jackson-annotations-2.21.jar;C:\Users\rajku\.m2\repository\tools\jackson\core\jackson-core\3.1.5\jackson-core-3.1.5.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-tomcat\4.1.1\spring-boot-starter-tomcat-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-tomcat-runtime\4.1.1\spring-boot-starter-tomcat-runtime-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-web-server\4.1.1\spring-boot-web-server-4.1.1.jar;C:\Users\rajku\.m2\repository\org\apache\tomcat\embed\tomcat-embed-core\11.0.24\tomcat-embed-core-11.0.24.jar;C:\Users\rajku\.m2\repository\org\apache\tomcat\embed\tomcat-embed-el\11.0.24\tomcat-embed-el-11.0.24.jar;C:\Users\rajku\.m2\repository\org\apache\tomcat\embed\tomcat-embed-websocket\11.0.24\tomcat-embed-websocket-11.0.24.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-tomcat\4.1.1\spring-boot-tomcat-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-http-converter\4.1.1\spring-boot-http-converter-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot\4.1.1\spring-boot-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-context\7.0.9\spring-context-7.0.9.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-web\7.0.9\spring-web-7.0.9.jar;C:\Users\rajku\.m2\repository\io\micrometer\micrometer-observation\1.17.1\micrometer-observation-1.17.1.jar;C:\Users\rajku\.m2\repository\io\micrometer\micrometer-commons\1.17.1\micrometer-commons-1.17.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-webmvc\4.1.1\spring-boot-webmvc-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-servlet\4.1.1\spring-boot-servlet-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-webmvc\7.0.9\spring-webmvc-7.0.9.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-expression\7.0.9\spring-expression-7.0.9.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-data-jpa\4.1.1\spring-boot-starter-data-jpa-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter\4.1.1\spring-boot-starter-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-logging\4.1.1\spring-boot-starter-logging-4.1.1.jar;C:\Users\rajku\.m2\repository\ch\qos\logback\logback-classic\1.5.38\logback-classic-1.5.38.jar;C:\Users\rajku\.m2\repository\ch\qos\logback\logback-core\1.5.38\logback-core-1.5.38.jar;C:\Users\rajku\.m2\repository\org\apache\logging\log4j\log4j-to-slf4j\2.25.5\log4j-to-slf4j-2.25.5.jar;C:\Users\rajku\.m2\repository\org\slf4j\jul-to-slf4j\2.0.18\jul-to-slf4j-2.0.18.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-autoconfigure\4.1.1\spring-boot-autoconfigure-4.1.1.jar;C:\Users\rajku\.m2\repository\jakarta\annotation\jakarta.annotation-api\3.0.0\jakarta.annotation-api-3.0.0.jar;C:\Users\rajku\.m2\repository\org\yaml\snakeyaml\2.6\snakeyaml-2.6.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-jdbc\4.1.1\spring-boot-starter-jdbc-4.1.1.jar;C:\Users\rajku\.m2\repository\com\zaxxer\HikariCP\7.0.2\HikariCP-7.0.2.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-data-jpa\4.1.1\spring-boot-data-jpa-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-data-commons\4.1.1\spring-boot-data-commons-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-persistence\4.1.1\spring-boot-persistence-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\data\spring-data-commons\4.1.1\spring-data-commons-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-hibernate\4.1.1\spring-boot-hibernate-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-jpa\4.1.1\spring-boot-jpa-4.1.1.jar;C:\Users\rajku\.m2\repository\jakarta\persistence\jakarta.persistence-api\3.2.0\jakarta.persistence-api-3.2.0.jar;C:\Users\rajku\.m2\repository\org\hibernate\orm\hibernate-core\7.4.5.Final\hibernate-core-7.4.5.Final.jar;C:\Users\rajku\.m2\repository\jakarta\transaction\jakarta.transaction-api\2.0.1\jakarta.transaction-api-2.0.1.jar;C:\Users\rajku\.m2\repository\org\jboss\logging\jboss-logging\3.6.3.Final\jboss-logging-3.6.3.Final.jar;C:\Users\rajku\.m2\repository\org\hibernate\models\hibernate-models\1.1.1\hibernate-models-1.1.1.jar;C:\Users\rajku\.m2\repository\org\glassfish\jaxb\jaxb-runtime\4.0.9\jaxb-runtime-4.0.9.jar;C:\Users\rajku\.m2\repository\org\glassfish\jaxb\jaxb-core\4.0.9\jaxb-core-4.0.9.jar;C:\Users\rajku\.m2\repository\org\glassfish\jaxb\txw2\4.0.9\txw2-4.0.9.jar;C:\Users\rajku\.m2\repository\com\sun\istack\istack-commons-runtime\4.1.2\istack-commons-runtime-4.1.2.jar;C:\Users\rajku\.m2\repository\jakarta\inject\jakarta.inject-api\2.0.1\jakarta.inject-api-2.0.1.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-orm\7.0.9\spring-orm-7.0.9.jar;C:\Users\rajku\.m2\repository\org\springframework\data\spring-data-jpa\4.1.1\spring-data-jpa-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-tx\7.0.9\spring-tx-7.0.9.jar;C:\Users\rajku\.m2\repository\org\antlr\antlr4-runtime\4.13.2\antlr4-runtime-4.13.2.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-aspects\7.0.9\spring-aspects-7.0.9.jar;C:\Users\rajku\.m2\repository\org\aspectj\aspectjweaver\1.9.25.1\aspectjweaver-1.9.25.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-jdbc\4.1.1\spring-boot-jdbc-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-sql\4.1.1\spring-boot-sql-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-transaction\4.1.1\spring-boot-transaction-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-jdbc\7.0.9\spring-jdbc-7.0.9.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-security\4.1.1\spring-boot-starter-security-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-security\4.1.1\spring-boot-security-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\security\spring-security-config\7.1.1\spring-security-config-7.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\security\spring-security-core\7.1.1\spring-security-core-7.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\security\spring-security-crypto\7.1.1\spring-security-crypto-7.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\security\spring-security-web\7.1.1\spring-security-web-7.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-aop\7.0.9\spring-aop-7.0.9.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-beans\7.0.9\spring-beans-7.0.9.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-thymeleaf\4.1.1\spring-boot-starter-thymeleaf-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-thymeleaf\4.1.1\spring-boot-thymeleaf-4.1.1.jar;C:\Users\rajku\.m2\repository\org\thymeleaf\thymeleaf-spring6\3.1.5.RELEASE\thymeleaf-spring6-3.1.5.RELEASE.jar;C:\Users\rajku\.m2\repository\org\thymeleaf\thymeleaf\3.1.5.RELEASE\thymeleaf-3.1.5.RELEASE.jar;C:\Users\rajku\.m2\repository\org\attoparser\attoparser\2.0.7.RELEASE\attoparser-2.0.7.RELEASE.jar;C:\Users\rajku\.m2\repository\org\unbescape\unbescape\1.1.6.RELEASE\unbescape-1.1.6.RELEASE.jar;C:\Users\rajku\.m2\repository\org\xerial\sqlite-jdbc\3.45.1.0\sqlite-jdbc-3.45.1.0.jar;C:\Users\rajku\.m2\repository\org\slf4j\slf4j-api\2.0.18\slf4j-api-2.0.18.jar;C:\Users\rajku\.m2\repository\org\apache\poi\poi-ooxml\5.3.0\poi-ooxml-5.3.0.jar;C:\Users\rajku\.m2\repository\org\apache\poi\poi\5.3.0\poi-5.3.0.jar;C:\Users\rajku\.m2\repository\commons-codec\commons-codec\1.21.0\commons-codec-1.21.0.jar;C:\Users\rajku\.m2\repository\org\apache\commons\commons-math3\3.6.1\commons-math3-3.6.1.jar;C:\Users\rajku\.m2\repository\com\zaxxer\SparseBitSet\1.3\SparseBitSet-1.3.jar;C:\Users\rajku\.m2\repository\org\apache\poi\poi-ooxml-lite\5.3.0\poi-ooxml-lite-5.3.0.jar;C:\Users\rajku\.m2\repository\org\apache\xmlbeans\xmlbeans\5.2.1\xmlbeans-5.2.1.jar;C:\Users\rajku\.m2\repository\org\apache\commons\commons-compress\1.26.2\commons-compress-1.26.2.jar;C:\Users\rajku\.m2\repository\org\apache\commons\commons-lang3\3.20.0\commons-lang3-3.20.0.jar;C:\Users\rajku\.m2\repository\commons-io\commons-io\2.16.1\commons-io-2.16.1.jar;C:\Users\rajku\.m2\repository\com\github\virtuald\curvesapi\1.08\curvesapi-1.08.jar;C:\Users\rajku\.m2\repository\org\apache\logging\log4j\log4j-api\2.25.5\log4j-api-2.25.5.jar;C:\Users\rajku\.m2\repository\org\apache\commons\commons-collections4\4.4\commons-collections4-4.4.jar;C:\Users\rajku\.m2\repository\org\apache\pdfbox\pdfbox\3.0.4\pdfbox-3.0.4.jar;C:\Users\rajku\.m2\repository\org\apache\pdfbox\pdfbox-io\3.0.4\pdfbox-io-3.0.4.jar;C:\Users\rajku\.m2\repository\org\apache\pdfbox\fontbox\3.0.4\fontbox-3.0.4.jar;C:\Users\rajku\.m2\repository\commons-logging\commons-logging\1.3.6\commons-logging-1.3.6.jar;C:\Users\rajku\.m2\repository\org\projectlombok\lombok\1.18.46\lombok-1.18.46.jar;C:\Users\rajku\.m2\repository\jakarta\xml\bind\jakarta.xml.bind-api\4.0.5\jakarta.xml.bind-api-4.0.5.jar;C:\Users\rajku\.m2\repository\jakarta\activation\jakarta.activation-api\2.1.4\jakarta.activation-api-2.1.4.jar;C:\Users\rajku\.m2\repository\net\bytebuddy\byte-buddy\1.18.11\byte-buddy-1.18.11.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-core\7.0.9\spring-core-7.0.9.jar;C:\Users\rajku\.m2\repository\org\jspecify\jspecify\1.0.1\jspecify-1.0.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-starter-mail\4.1.1\spring-boot-starter-mail-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\boot\spring-boot-mail\4.1.1\spring-boot-mail-4.1.1.jar;C:\Users\rajku\.m2\repository\org\springframework\spring-context-support\7.0.9\spring-context-support-7.0.9.jar;C:\Users\rajku\.m2\repository\jakarta\mail\jakarta.mail-api\2.1.5\jakarta.mail-api-2.1.5.jar;C:\Users\rajku\.m2\repository\org\eclipse\angus\angus-mail\2.0.5\angus-mail-2.0.5.jar;C:\Users\rajku\.m2\repository\org\eclipse\angus\angus-activation\2.0.3\angus-activation-2.0.3.jar;C:\Users\rajku\.m2\repository\com\sun\mail\jakarta.mail\2.0.1\jakarta.mail-2.0.1.jar;C:\Users\rajku\.m2\repository\com\sun\activation\jakarta.activation\2.0.1\jakarta.activation-2.0.1.jar;C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.2\lib\idea_rt.jar" com.artech.portfolio.PortfolioApplication
   Connected to the target VM, address: '127.0.0.1:63768', transport: 'socket'

.   ____          _            __ _ _
/\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
\\/  ___)| |_)| | | | | || (_| |  ) ) ) )
'  |____| .__|_| |_|_| |_\__, | / / / /
=========|_|==============|___/=/_/_/_/

:: Spring Boot ::                (v4.1.1)

2026-10-30T20:33:15.983+05:30  INFO 29508 --- [portfolio] [           main] c.artech.portfolio.PortfolioApplication  : Starting PortfolioApplication using Java 21.0.8 with PID 29508 (C:\Drive-D\investment\AI\portfolio-ai\target\classes started by rajku in C:\Drive-D\investment\AI\portfolio-ai)
2026-10-30T20:33:16.005+05:30  INFO 29508 --- [portfolio] [           main] c.artech.portfolio.PortfolioApplication  : No active profile set, falling back to 1 default profile: "default"
2026-10-30T20:33:18.861+05:30  INFO 29508 --- [portfolio] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Bootstrapping Spring Data JPA repositories in DEFAULT mode.
2026-10-30T20:33:19.331+05:30  INFO 29508 --- [portfolio] [           main] .s.d.r.c.RepositoryConfigurationDelegate : Finished Spring Data repository scanning in 431 ms. Found 21 JPA repository interfaces.
2026-10-30T20:33:21.571+05:30  INFO 29508 --- [portfolio] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat initialized with port 8082 (http)
2026-10-30T20:33:21.614+05:30  INFO 29508 --- [portfolio] [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2026-10-30T20:33:21.615+05:30  INFO 29508 --- [portfolio] [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/11.0.24]
2026-10-30T20:33:21.808+05:30  INFO 29508 --- [portfolio] [           main] b.w.c.s.WebApplicationContextInitializer : Root WebApplicationContext: initialization completed in 5482 ms
2026-10-30T20:33:21.970+05:30  INFO 29508 --- [portfolio] [           main] c.a.p.maintenance.MaintenanceService     : Database maintenance ready - database file: C:\Users\rajku\portfolio.db, log file: C:\Users\rajku\springboot.log, drain pause: 8000 ms
2026-10-30T20:33:22.479+05:30  INFO 29508 --- [portfolio] [           main] org.hibernate.orm.jpa                    : HHH008540: Processing PersistenceUnitInfo [name: default]
2026-10-30T20:33:22.608+05:30  INFO 29508 --- [portfolio] [           main] org.hibernate.orm.core                   : HHH000001: Hibernate ORM core version 7.4.5.Final
2026-10-30T20:33:23.924+05:30  INFO 29508 --- [portfolio] [           main] o.s.o.j.p.SpringPersistenceUnitInfo      : No LoadTimeWeaver setup: ignoring JPA class transformer
2026-10-30T20:33:24.035+05:30  INFO 29508 --- [portfolio] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Starting...
2026-10-30T20:33:24.301+05:30  INFO 29508 --- [portfolio] [           main] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Added connection org.sqlite.jdbc4.JDBC4Connection@18b45500
2026-10-30T20:33:24.305+05:30  INFO 29508 --- [portfolio] [           main] com.zaxxer.hikari.HikariDataSource       : HikariPool-1 - Start completed.
2026-10-30T20:33:24.366+05:30  INFO 29508 --- [portfolio] [           main] org.hibernate.orm.connections.pooling    : HHH10001005: Database info:
Database JDBC URL [jdbc:sqlite:C:\Users\rajku/portfolio.db?busy_timeout=10000&journal_mode=WAL&synchronous=NORMAL]
Database driver: SQLite JDBC
Database dialect: SQLiteDialect
Database version: 0.0
Default catalog/schema: undefined/undefined
Autocommit mode: undefined/unknown
Isolation level: SERIALIZABLE [default SERIALIZABLE]
JDBC fetch size: none
Pool: DataSourceConnectionProvider
Minimum pool size: undefined/unknown
Maximum pool size: undefined/unknown
2026-10-30T20:33:26.839+05:30  INFO 29508 --- [portfolio] [           main] org.hibernate.orm.core                   : HHH000489: No JTA platform available (set 'hibernate.transaction.jta.platform' to enable JTA platform integration)
2026-10-30T20:33:26.945+05:30  INFO 29508 --- [portfolio] [           main] j.LocalContainerEntityManagerFactoryBean : Initialized JPA EntityManagerFactory for persistence unit 'default'
2026-10-30T20:33:27.161+05:30  INFO 29508 --- [portfolio] [           main] o.s.d.j.r.query.QueryEnhancerFactories   : Hibernate is in classpath; If applicable, HQL parser will be used.
Hibernate: select apc1_0.id,apc1_0.client_id,apc1_0.investment_type,apc1_0.note,apc1_0.param_name,apc1_0.param_value from app_param_config apc1_0
Application Param Config refreshed at 2026-10-30T20:33:28.385209100 | Refresh rate: PT15M
Hibernate: select apc1_0.id,apc1_0.client_id,apc1_0.investment_type,apc1_0.note,apc1_0.param_name,apc1_0.param_value from app_param_config apc1_0 where apc1_0.investment_type=? and apc1_0.param_name=?
2026-10-30T20:33:30.773+05:30  WARN 29508 --- [portfolio] [           main] JpaBaseConfiguration$JpaWebConfiguration : spring.jpa.open-in-view is enabled by default. Therefore, database queries may be performed during view rendering. Explicitly configure spring.jpa.open-in-view to disable this warning
2026-10-30T20:33:31.091+05:30  INFO 29508 --- [portfolio] [           main] r$InitializeUserDetailsManagerConfigurer : Global AuthenticationManager configured with UserDetailsService bean with name portfolioUserDetailsService
2026-10-30T20:33:31.569+05:30  INFO 29508 --- [portfolio] [           main] o.s.b.w.a.WelcomePageHandlerMapping      : Adding welcome page template: index
2026-10-30T20:33:32.357+05:30  INFO 29508 --- [portfolio] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 8082 (http) with context path '/'
2026-10-30T20:33:32.363+05:30  INFO 29508 --- [portfolio] [           main] c.a.p.maintenance.BondV9Migration        : BONDBP_V9 schema migration complete (bond_sip rename, bond backfill, bond_sip_transaction dropped)
2026-10-30T20:33:32.364+05:30  INFO 29508 --- [portfolio] [           main] c.a.portfolio.maintenance.FdV1Migration  : FD_V1 schema migration complete (fixed_deposit.tenure_months dropped)
2026-10-30T20:33:32.406+05:30  INFO 29508 --- [portfolio] [           main] c.artech.portfolio.PortfolioApplication  : Started PortfolioApplication in 18.52 seconds (process running for 20.538)
Hibernate: select bs1_0.id,bs1_0.client_id,bs1_0.frequency,bs1_0.investment_date,bs1_0.issuer,bs1_0.sip_amount,bs1_0.sip_date,bs1_0.status from bond_sip bs1_0 where bs1_0.status in (?) and bs1_0.sip_date<=? order by bs1_0.sip_date
Hibernate: select b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units from bond b1_0 where b1_0.bond_sip_id=?
Hibernate: select apc1_0.id,apc1_0.client_id,apc1_0.investment_type,apc1_0.note,apc1_0.param_name,apc1_0.param_value from app_param_config apc1_0
2026-10-30T20:33:32.500+05:30  INFO 29508 --- [portfolio] [     virtual-56] c.a.p.bondsip.service.BondSipScheduler   : Bond SIP restart sync finished, 0 new bond row(s) created
Application Param Config refreshed at 2026-10-30T20:33:32.509763100 | Refresh rate: PT15M
2026-10-30T20:33:42.316+05:30  INFO 29508 --- [portfolio] [nio-8082-exec-1] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring DispatcherServlet 'dispatcherServlet'
2026-10-30T20:33:42.317+05:30  INFO 29508 --- [portfolio] [nio-8082-exec-1] o.s.web.servlet.DispatcherServlet        : Initializing Servlet 'dispatcherServlet'
2026-10-30T20:33:42.319+05:30  INFO 29508 --- [portfolio] [nio-8082-exec-1] o.s.web.servlet.DispatcherServlet        : Completed initialization in 2 ms
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units from bond b1_0 where b1_0.client_id in (?)
Hibernate: select bcf1_0.id,bcf1_0.amount,bcf1_0.bond_id,b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units,bcf1_0.date,bcf1_0.payout,bcf1_0.schedule,bcf1_0.value_pct,bcf1_0.year from bond_cash_flow bcf1_0 join bond b1_0 on b1_0.id=bcf1_0.bond_id where b1_0.client_id in (?) order by bcf1_0.date,bcf1_0.id
Hibernate: select fm1_0.client_id,fm1_0.id,fm1_0.member_client_id,m1_0.id,m1_0.failed_attempts,m1_0.locked_until,m1_0.name,m1_0.pan,m1_0.password_hash,m1_0.reset_token_expiry,m1_0.reset_token_hash,m1_0.role,m1_0.status from client_family_member fm1_0 left join client m1_0 on m1_0.id=fm1_0.member_client_id where fm1_0.client_id=?
Hibernate: select b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units from bond b1_0 where b1_0.client_id in (?,?)
Hibernate: select bcf1_0.id,bcf1_0.amount,bcf1_0.bond_id,b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units,bcf1_0.date,bcf1_0.payout,bcf1_0.schedule,bcf1_0.value_pct,bcf1_0.year from bond_cash_flow bcf1_0 join bond b1_0 on b1_0.id=bcf1_0.bond_id where b1_0.client_id in (?,?) order by bcf1_0.date,bcf1_0.id
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select fm1_0.client_id,fm1_0.id,fm1_0.member_client_id,m1_0.id,m1_0.failed_attempts,m1_0.locked_until,m1_0.name,m1_0.pan,m1_0.password_hash,m1_0.reset_token_expiry,m1_0.reset_token_hash,m1_0.role,m1_0.status from client_family_member fm1_0 left join client m1_0 on m1_0.id=fm1_0.member_client_id where fm1_0.client_id=?
Hibernate: select mfst1_0.id,mfst1_0.amount,mfst1_0.balance,mfst1_0.description,mfst1_0.dividend_rate,mfst1_0.gift_folio,mfst1_0.nav,mfst1_0.owner_pan,mfst1_0.scheme_id,mfst1_0.txn_date,mfst1_0.type,mfst1_0.units from mf_folio_scheme_transactions mfst1_0 where mfst1_0.owner_pan in (?)
Hibernate: select mfsv1_0.id,mfsv1_0.cost_value,mfsv1_0.market_value,mfsv1_0.nav,mfsv1_0.owner_pan,mfsv1_0.scheme_id,mfsv1_0.value_date from mf_folio_schemes_valuation mfsv1_0 where mfsv1_0.owner_pan in (?)
Hibernate: select mfst1_0.id,mfst1_0.amount,mfst1_0.balance,mfst1_0.description,mfst1_0.dividend_rate,mfst1_0.gift_folio,mfst1_0.nav,mfst1_0.owner_pan,mfst1_0.scheme_id,mfst1_0.txn_date,mfst1_0.type,mfst1_0.units from mf_folio_scheme_transactions mfst1_0 where mfst1_0.owner_pan in (?,?)
Hibernate: select mfsv1_0.id,mfsv1_0.cost_value,mfsv1_0.market_value,mfsv1_0.nav,mfsv1_0.owner_pan,mfsv1_0.scheme_id,mfsv1_0.value_date from mf_folio_schemes_valuation mfsv1_0 where mfsv1_0.owner_pan in (?,?)
Hibernate: select mps1_0.id,mps1_0.amc_id,mps1_0.cost_value,mps1_0.market_value,mps1_0.match_condition,mps1_0.owner_pan,mps1_0.refresh_from,mps1_0.is_total from mf_portfolio_summary mps1_0 where mps1_0.owner_pan in (?) and mps1_0.is_total=1
Hibernate: select mps1_0.id,mps1_0.amc_id,mps1_0.cost_value,mps1_0.market_value,mps1_0.match_condition,mps1_0.owner_pan,mps1_0.refresh_from,mps1_0.is_total from mf_portfolio_summary mps1_0 where mps1_0.owner_pan in (?,?) and mps1_0.is_total=1
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
2026-10-30T20:34:52.347+05:30  INFO 29508 --- [portfolio] [nio-8082-exec-4] DBQUERY_AUDIT                            : admin=AIGPR6090R category=DML statements=15 rows=0 ms=12 outcome=SUCCESS sql=DELETE FROM mf_amc where 1 = 1; DELETE FROM mf_folio_scheme_transactions where 1 = 1; DELETE FROM mf_folio_schemes where 1 = 1; DELETE FROM mf_folio_schemes_nominees where 1 = 1; DELETE FROM mf_folio_schemes_valuation where 1 = 1; DELETE FROM mf_folios where 1 = 1; DELETE FROM mf_import_batch where ...
2026-10-30T20:34:55.655+05:30  WARN 29508 --- [portfolio] [nio-8082-exec-8] DBQUERY_AUDIT                            : admin=AIGPR6090R category=DML ms=6 outcome=ERROR error=[SQLITE_ERROR] SQL error or missing database (cannot commit - no transaction is active) sql=DELETE FROM mf_amc where 1 = 1; DELETE FROM mf_folio_scheme_transactions where 1 = 1; DELETE FROM mf_folio_schemes where 1 = 1; DELETE FROM mf_folio_schemes_nominees where 1 = 1; DELETE FROM mf_folio_schemes_valuation where 1 = 1; DELETE FROM mf_folios where 1 = 1; DELETE FROM mf_import_batch where ...
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
Hibernate: select b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units from bond b1_0 where b1_0.client_id in (?)
Hibernate: select bcf1_0.id,bcf1_0.amount,bcf1_0.bond_id,b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units,bcf1_0.date,bcf1_0.payout,bcf1_0.schedule,bcf1_0.value_pct,bcf1_0.year from bond_cash_flow bcf1_0 join bond b1_0 on b1_0.id=bcf1_0.bond_id where b1_0.client_id in (?) order by bcf1_0.date,bcf1_0.id
Hibernate: select fm1_0.client_id,fm1_0.id,fm1_0.member_client_id,m1_0.id,m1_0.failed_attempts,m1_0.locked_until,m1_0.name,m1_0.pan,m1_0.password_hash,m1_0.reset_token_expiry,m1_0.reset_token_hash,m1_0.role,m1_0.status from client_family_member fm1_0 left join client m1_0 on m1_0.id=fm1_0.member_client_id where fm1_0.client_id=?
Hibernate: select b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units from bond b1_0 where b1_0.client_id in (?,?)
Hibernate: select bcf1_0.id,bcf1_0.amount,bcf1_0.bond_id,b1_0.id,b1_0.bond_name,b1_0.bond_sip_id,b1_0.client_id,b1_0.coupon_frequency,b1_0.coupon_rate,b1_0.coupon_type,b1_0.credit_rating,b1_0.face_value_per_unit,b1_0.interest_payment_start_date,b1_0.investment_amount_per_unit,b1_0.investment_date,b1_0.investment_type,b1_0.issuer,b1_0.listing,b1_0.maturity_date,b1_0.notes,b1_0.repayment_priority,b1_0.staggered_bond,b1_0.status,b1_0.type_of_security,b1_0.units,bcf1_0.date,bcf1_0.payout,bcf1_0.schedule,bcf1_0.value_pct,bcf1_0.year from bond_cash_flow bcf1_0 join bond b1_0 on b1_0.id=bcf1_0.bond_id where b1_0.client_id in (?,?) order by bcf1_0.date,bcf1_0.id
Hibernate: select c1_0.id,c1_0.failed_attempts,c1_0.locked_until,c1_0.name,c1_0.pan,c1_0.password_hash,c1_0.reset_token_expiry,c1_0.reset_token_hash,c1_0.role,c1_0.status from client c1_0 where c1_0.pan=?
2026-10-30T20:35:04.361+05:30  WARN 29508 --- [portfolio] [nio-8082-exec-1] org.hibernate.orm.jdbc.error             : HHH000247: ErrorCode: 1, SQLState: null
Hibernate: select fm1_0.client_id,fm1_0.id,fm1_0.member_client_id,m1_0.id,m1_0.failed_attempts,m1_0.locked_until,m1_0.name,m1_0.pan,m1_0.password_hash,m1_0.reset_token_expiry,m1_0.reset_token_hash,m1_0.role,m1_0.status from client_family_member fm1_0 left join client m1_0 on m1_0.id=fm1_0.member_client_id where fm1_0.client_id=?
2026-10-30T20:35:04.363+05:30  WARN 29508 --- [portfolio] [nio-8082-exec-1] org.hibernate.orm.jdbc.error             : [SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)
Hibernate: select mfst1_0.id,mfst1_0.amount,mfst1_0.balance,mfst1_0.description,mfst1_0.dividend_rate,mfst1_0.gift_folio,mfst1_0.nav,mfst1_0.owner_pan,mfst1_0.scheme_id,mfst1_0.txn_date,mfst1_0.type,mfst1_0.units from mf_folio_scheme_transactions mfst1_0 where mfst1_0.owner_pan in (?)
Hibernate: select mfsv1_0.id,mfsv1_0.cost_value,mfsv1_0.market_value,mfsv1_0.nav,mfsv1_0.owner_pan,mfsv1_0.scheme_id,mfsv1_0.value_date from mf_folio_schemes_valuation mfsv1_0 where mfsv1_0.owner_pan in (?)
Hibernate: select mfst1_0.id,mfst1_0.amount,mfst1_0.balance,mfst1_0.description,mfst1_0.dividend_rate,mfst1_0.gift_folio,mfst1_0.nav,mfst1_0.owner_pan,mfst1_0.scheme_id,mfst1_0.txn_date,mfst1_0.type,mfst1_0.units from mf_folio_scheme_transactions mfst1_0 where mfst1_0.owner_pan in (?,?)
Hibernate: select mfsv1_0.id,mfsv1_0.cost_value,mfsv1_0.market_value,mfsv1_0.nav,mfsv1_0.owner_pan,mfsv1_0.scheme_id,mfsv1_0.value_date from mf_folio_schemes_valuation mfsv1_0 where mfsv1_0.owner_pan in (?,?)
2026-10-30T20:35:04.365+05:30 ERROR 29508 --- [portfolio] [nio-8082-exec-1] o.s.orm.jpa.EntityManagerFactoryUtils    : Failed to release JPA EntityManager

org.hibernate.exception.GenericJDBCException: Unable to release JDBC Connection [[SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)] [n/a]
at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:39) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:115) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:101) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.release(LogicalConnectionManagedImpl.java:212) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.releaseConnectionIfNeeded(LogicalConnectionManagedImpl.java:104) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.close(LogicalConnectionManagedImpl.java:264) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.jdbc.internal.JdbcCoordinatorImpl.close(JdbcCoordinatorImpl.java:160) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.AbstractSharedSessionContract.close(AbstractSharedSessionContract.java:576) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionImpl.closeWithoutOpenChecks(SessionImpl.java:392) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionImpl.close(SessionImpl.java:374) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
at org.springframework.orm.jpa.ExtendedEntityManagerCreator$ExtendedEntityManagerInvocationHandler.invoke(ExtendedEntityManagerCreator.java:360) ~[spring-orm-7.0.9.jar:7.0.9]
at jdk.proxy2/jdk.proxy2.$Proxy132.close(Unknown Source) ~[na:na]
at org.springframework.orm.jpa.EntityManagerFactoryUtils.closeEntityManager(EntityManagerFactoryUtils.java:535) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor.afterCompletion(OpenEntityManagerInViewInterceptor.java:112) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter.afterCompletion(WebRequestHandlerInterceptorAdapter.java:73) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.HandlerExecutionChain.triggerAfterCompletion(HandlerExecutionChain.java:175) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.triggerAfterCompletion(DispatcherServlet.java:1361) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:983) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:866) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1000) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:892) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:622) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:874) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:710) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:132) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:59) ~[tomcat-embed-websocket-11.0.24.jar:11.0.24]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at com.artech.portfolio.maintenance.MaintenanceFilter.doFilterInternal(MaintenanceFilter.java:57) ~[classes/:na]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$2(FilterChainProxy.java:235) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:376) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:101) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:181) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:245) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:239) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:110) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:96) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.csrf.CsrfFilter.doFilterInternal(CsrfFilter.java:118) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:237) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:195) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.ServletRequestPathFilter.doFilter(ServletRequestPathFilter.java:52) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebSecurityConfiguration.java:317) ~[spring-security-config-7.1.1.jar:7.1.1]
at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:355) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:272) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:199) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:77) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:535) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:86) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:347) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:406) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1307) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:2036) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:949) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:483) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]
Caused by: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.exec(DB.java:198) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.SQLiteConnection.rollback(SQLiteConnection.java:460) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyConnection.close(ProxyConnection.java:250) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.engine.jdbc.connections.internal.DataSourceConnectionProvider.closeConnection(DataSourceConnectionProvider.java:162) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.NonContextualJdbcConnectionAccess.releaseConnection(NonContextualJdbcConnectionAccess.java:85) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.release(LogicalConnectionManagedImpl.java:208) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 99 common frames omitted

Hibernate: select mps1_0.id,mps1_0.amc_id,mps1_0.cost_value,mps1_0.market_value,mps1_0.match_condition,mps1_0.owner_pan,mps1_0.refresh_from,mps1_0.is_total from mf_portfolio_summary mps1_0 where mps1_0.owner_pan in (?) and mps1_0.is_total=1
Hibernate: select mps1_0.id,mps1_0.amc_id,mps1_0.cost_value,mps1_0.market_value,mps1_0.match_condition,mps1_0.owner_pan,mps1_0.refresh_from,mps1_0.is_total from mf_portfolio_summary mps1_0 where mps1_0.owner_pan in (?,?) and mps1_0.is_total=1
2026-10-30T20:35:04.382+05:30 ERROR 29508 --- [portfolio] [nio-8082-exec-1] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.orm.jpa.JpaSystemException: Unable to commit against JDBC Connection] with root cause

org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (cannot commit - no transaction is active)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.exec(DB.java:198) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.SQLiteConnection.commit(SQLiteConnection.java:449) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyConnection.commit(ProxyConnection.java:378) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyConnection.commit(HikariProxyConnection.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.resource.jdbc.internal.AbstractLogicalConnectionImplementor.commitConnection(AbstractLogicalConnectionImplementor.java:95) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.AbstractLogicalConnectionImplementor.commit(AbstractLogicalConnectionImplementor.java:82) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl$TransactionDriverControlImpl.commitNoRollbackOnly(JdbcResourceLocalTransactionCoordinatorImpl.java:273) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl$TransactionDriverControlImpl.commit(JdbcResourceLocalTransactionCoordinatorImpl.java:242) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.transaction.internal.TransactionImpl.commit(TransactionImpl.java:89) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:552) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.transaction.support.AbstractPlatformTransactionManager.processCommit(AbstractPlatformTransactionManager.java:794) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.support.AbstractPlatformTransactionManager.commit(AbstractPlatformTransactionManager.java:757) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.interceptor.TransactionAspectSupport.commitTransactionAfterReturning(TransactionAspectSupport.java:687) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:408) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:130) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:179) ~[spring-aop-7.0.9.jar:7.0.9]
at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:719) ~[spring-aop-7.0.9.jar:7.0.9]
at com.artech.portfolio.summary.service.impl.PortfolioSummaryServiceImpl$$SpringCGLIB$$0.getBondWidget(<generated>) ~[classes/:na]
at com.artech.portfolio.summary.controller.PortfolioSummaryController.bonds(PortfolioSummaryController.java:34) ~[classes/:na]
at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:252) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:184) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:934) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:853) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:86) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:963) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:866) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1000) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:892) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:622) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:874) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:710) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:132) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:59) ~[tomcat-embed-websocket-11.0.24.jar:11.0.24]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at com.artech.portfolio.maintenance.MaintenanceFilter.doFilterInternal(MaintenanceFilter.java:57) ~[classes/:na]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$2(FilterChainProxy.java:235) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:376) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:101) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:181) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:245) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:239) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:110) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:96) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.csrf.CsrfFilter.doFilterInternal(CsrfFilter.java:118) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:237) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:195) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.ServletRequestPathFilter.doFilter(ServletRequestPathFilter.java:52) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebSecurityConfiguration.java:317) ~[spring-security-config-7.1.1.jar:7.1.1]
at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:355) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:272) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:199) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:77) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:535) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:86) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:347) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:406) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1307) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:2036) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:949) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:483) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]
Suppressed: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.exec(DB.java:198) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.SQLiteConnection.rollback(SQLiteConnection.java:460) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyConnection.rollback(ProxyConnection.java:386) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyConnection.rollback(HikariProxyConnection.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.resource.jdbc.internal.AbstractLogicalConnectionImplementor.commitConnection(AbstractLogicalConnectionImplementor.java:105) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 106 common frames omitted

2026-10-30T20:35:04.384+05:30  WARN 29508 --- [portfolio] [nio-8082-exec-6] org.hibernate.orm.jdbc.error             : HHH000247: ErrorCode: 1, SQLState: null
2026-10-30T20:35:04.384+05:30  WARN 29508 --- [portfolio] [nio-8082-exec-6] org.hibernate.orm.jdbc.error             : [SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)
2026-10-30T20:35:04.384+05:30 ERROR 29508 --- [portfolio] [nio-8082-exec-6] o.s.orm.jpa.EntityManagerFactoryUtils    : Failed to release JPA EntityManager

org.hibernate.exception.GenericJDBCException: Unable to release JDBC Connection [[SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)] [n/a]
at org.hibernate.exception.internal.StandardSQLExceptionConverter.convert(StandardSQLExceptionConverter.java:39) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:115) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.jdbc.spi.SqlExceptionHelper.convert(SqlExceptionHelper.java:101) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.release(LogicalConnectionManagedImpl.java:212) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.releaseConnectionIfNeeded(LogicalConnectionManagedImpl.java:104) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.close(LogicalConnectionManagedImpl.java:264) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.jdbc.internal.JdbcCoordinatorImpl.close(JdbcCoordinatorImpl.java:160) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.AbstractSharedSessionContract.close(AbstractSharedSessionContract.java:576) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionImpl.closeWithoutOpenChecks(SessionImpl.java:392) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.SessionImpl.close(SessionImpl.java:374) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
at org.springframework.orm.jpa.ExtendedEntityManagerCreator$ExtendedEntityManagerInvocationHandler.invoke(ExtendedEntityManagerCreator.java:360) ~[spring-orm-7.0.9.jar:7.0.9]
at jdk.proxy2/jdk.proxy2.$Proxy132.close(Unknown Source) ~[na:na]
at org.springframework.orm.jpa.EntityManagerFactoryUtils.closeEntityManager(EntityManagerFactoryUtils.java:535) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor.afterCompletion(OpenEntityManagerInViewInterceptor.java:112) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.handler.WebRequestHandlerInterceptorAdapter.afterCompletion(WebRequestHandlerInterceptorAdapter.java:73) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.HandlerExecutionChain.triggerAfterCompletion(HandlerExecutionChain.java:175) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.triggerAfterCompletion(DispatcherServlet.java:1361) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:983) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:866) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1000) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:892) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:622) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:874) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:710) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:132) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:59) ~[tomcat-embed-websocket-11.0.24.jar:11.0.24]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at com.artech.portfolio.maintenance.MaintenanceFilter.doFilterInternal(MaintenanceFilter.java:57) ~[classes/:na]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$2(FilterChainProxy.java:235) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:376) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:101) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:181) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:245) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:239) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:110) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:96) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.csrf.CsrfFilter.doFilterInternal(CsrfFilter.java:118) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:237) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:195) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.ServletRequestPathFilter.doFilter(ServletRequestPathFilter.java:52) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebSecurityConfiguration.java:317) ~[spring-security-config-7.1.1.jar:7.1.1]
at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:355) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:272) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:199) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:77) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:535) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:86) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:347) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:406) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1307) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:2036) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:949) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:483) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]
Caused by: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.exec(DB.java:198) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.SQLiteConnection.rollback(SQLiteConnection.java:460) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyConnection.close(ProxyConnection.java:250) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.engine.jdbc.connections.internal.DataSourceConnectionProvider.closeConnection(DataSourceConnectionProvider.java:162) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.internal.NonContextualJdbcConnectionAccess.releaseConnection(NonContextualJdbcConnectionAccess.java:85) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.LogicalConnectionManagedImpl.release(LogicalConnectionManagedImpl.java:208) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 99 common frames omitted

2026-10-30T20:35:04.389+05:30 ERROR 29508 --- [portfolio] [nio-8082-exec-6] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.orm.jpa.JpaSystemException: Unable to commit against JDBC Connection] with root cause

org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (cannot commit - no transaction is active)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.exec(DB.java:198) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.SQLiteConnection.commit(SQLiteConnection.java:449) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyConnection.commit(ProxyConnection.java:378) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyConnection.commit(HikariProxyConnection.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.resource.jdbc.internal.AbstractLogicalConnectionImplementor.commitConnection(AbstractLogicalConnectionImplementor.java:95) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.jdbc.internal.AbstractLogicalConnectionImplementor.commit(AbstractLogicalConnectionImplementor.java:82) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl$TransactionDriverControlImpl.commitNoRollbackOnly(JdbcResourceLocalTransactionCoordinatorImpl.java:273) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorImpl$TransactionDriverControlImpl.commit(JdbcResourceLocalTransactionCoordinatorImpl.java:242) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.hibernate.engine.transaction.internal.TransactionImpl.commit(TransactionImpl.java:89) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
at org.springframework.orm.jpa.JpaTransactionManager.doCommit(JpaTransactionManager.java:552) ~[spring-orm-7.0.9.jar:7.0.9]
at org.springframework.transaction.support.AbstractPlatformTransactionManager.processCommit(AbstractPlatformTransactionManager.java:794) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.support.AbstractPlatformTransactionManager.commit(AbstractPlatformTransactionManager.java:757) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.interceptor.TransactionAspectSupport.commitTransactionAfterReturning(TransactionAspectSupport.java:687) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.interceptor.TransactionAspectSupport.invokeWithinTransaction(TransactionAspectSupport.java:408) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.transaction.interceptor.TransactionInterceptor.invoke(TransactionInterceptor.java:130) ~[spring-tx-7.0.9.jar:7.0.9]
at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:179) ~[spring-aop-7.0.9.jar:7.0.9]
at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:719) ~[spring-aop-7.0.9.jar:7.0.9]
at com.artech.portfolio.summary.service.impl.PortfolioSummaryServiceImpl$$SpringCGLIB$$0.getMfWidget(<generated>) ~[classes/:na]
at com.artech.portfolio.summary.controller.PortfolioSummaryController.mutualFunds(PortfolioSummaryController.java:44) ~[classes/:na]
at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:252) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:184) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:117) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:934) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:853) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:86) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:963) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:866) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1000) ~[spring-webmvc-7.0.9.jar:7.0.9]
at org.springframework.web.servlet.FrameworkServlet.doGet(FrameworkServlet.java:892) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:622) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:874) ~[spring-webmvc-7.0.9.jar:7.0.9]
at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:710) ~[tomcat-embed-core-11.0.24.jar:6.1]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:132) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:59) ~[tomcat-embed-websocket-11.0.24.jar:11.0.24]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at com.artech.portfolio.maintenance.MaintenanceFilter.doFilterInternal(MaintenanceFilter.java:57) ~[classes/:na]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$2(FilterChainProxy.java:235) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:376) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:101) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:126) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:120) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:181) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:245) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter.doFilter(AbstractAuthenticationProcessingFilter.java:239) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:110) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:96) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.csrf.CsrfFilter.doFilterInternal(CsrfFilter.java:118) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.web.FilterChainProxy$VirtualFilterChain.doFilter(FilterChainProxy.java:385) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:237) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:195) ~[spring-security-web-7.1.1.jar:7.1.1]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.ServletRequestPathFilter.doFilter(ServletRequestPathFilter.java:52) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebSecurityConfiguration.java:317) ~[spring-security-config-7.1.1.jar:7.1.1]
at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:355) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:272) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:199) ~[spring-web-7.0.9.jar:7.0.9]
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-7.0.9.jar:7.0.9]
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:111) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:77) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:535) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:86) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:347) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:406) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:71) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1307) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:2036) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:949) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:483) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:74) ~[tomcat-embed-core-11.0.24.jar:11.0.24]
at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]
Suppressed: org.sqlite.SQLiteException: [SQLITE_ERROR] SQL error or missing database (cannot rollback - no transaction is active)
at org.sqlite.core.DB.newSQLException(DB.java:1179) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.newSQLException(DB.java:1190) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.throwex(DB.java:1150) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.core.DB.exec(DB.java:198) ~[sqlite-jdbc-3.45.1.0.jar:na]
at org.sqlite.SQLiteConnection.rollback(SQLiteConnection.java:460) ~[sqlite-jdbc-3.45.1.0.jar:na]
at com.zaxxer.hikari.pool.ProxyConnection.rollback(ProxyConnection.java:386) ~[HikariCP-7.0.2.jar:na]
at com.zaxxer.hikari.pool.HikariProxyConnection.rollback(HikariProxyConnection.java) ~[HikariCP-7.0.2.jar:na]
at org.hibernate.resource.jdbc.internal.AbstractLogicalConnectionImplementor.commitConnection(AbstractLogicalConnectionImplementor.java:105) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
... 106 common frames omitted

