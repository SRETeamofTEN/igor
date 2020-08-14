def checkStatuses = "".toString();
def toAddress = "DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()
def toAddressFailover = "matthias.gradl@sap.com,valentin.zipf@sap.com, DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()

//Email has to be redone after adding thousand eyes and Dynatrace integrations in 2 more stages

pipeline {
      options {
      timeout(time: 10, unit: 'MINUTES')
  }
    agent any
    environment {
        checkInAvsFailed = false;
        checkInDirectFailed = false
        checkInThousandEyes = false
        checkInElkConnsToDomainDB = false
        checkInElkDomainDBFailover = false
        checkInDomainDBMetrics = false
        checkInElkBorrowedConnectionsReached = false
        checkInElkUnableToReadUM = false
        checkInElkUnexpectedResponse = false
		checkInElkDomainDBSlow = false
		checkInElkSendToAuditLog = false
		checkInElkWriteToAudit = false
		checkInDynatrace = false



        //python variables
        EVAL_DATA = '$ENV_EVAL_DATA'
        DIRECT_URL = '$ENV_DIRECT_URL'

        //Java for Variables General Deploy issues

        DC = '$WHERE'

        AUTH = credentials("puser")
        TARGET = 'ELK'  //could be LOG or ELK depending on where to search
        TARGET2 = 'LOG'  //could be LOG or ELK depending on where to search
        elkLogStage = "Check Logs for $TARGET"

        //Java Variables for Check 2 and 3 about profiling logs.
        CHECK_NAME1 = 'CONNSTODOMAINDB'
        CHECK_NAME2 = 'DOMAINDB_FAILOVER_OCCURED'
        CHECK_NAME3 = 'DOMAINDB_POOLED_CONNECTIONS'
        CHECK_NAME4 = 'ORCHDOMAINDB2'
        CHECK_NAME5 = 'ORCHTODOMAINDB'
		CHECK_NAME6 = 'DEPLOY_PERFORMANCE'
		CHECK_NAME7 = 'DOMAINDB_AUDITLOG'
		CHECK_NAME8 = 'DOMAINDB_AUDITLOG2'

    }

    stages {
        
        stage('CLEAN') {
            failFast true
            steps {
                cleanWs()
            }
        }
        stage('Checking monitor in AvS and Directly') {
            steps {
                script {
                    try {
                    CHECK_IN_AVS = sh(script: "python $ENV_JOBS/avs_requests.py  $EVAL_DATA $DIRECT_URL", returnStdout: true).toString().trim()
                    echo CHECK_IN_AVS
                    if (CHECK_IN_AVS.toString().contains("DOWN")) {
                        checkInAvsFailed = true
                        checkStatuses = checkStatuses.concat("1. AvS status: DOWN\n")
                        unstable("Check status in AVS is DOWN")
                     }

                     else {

                        checkStatuses = checkStatuses.concat("1. AvS status: UP\n")
                     }

                    if (CHECK_IN_AVS.toString().contains("500")) {
                        checkInAvsFailed = true
                        checkInDirectFailed = true
                        checkStatuses = checkStatuses.concat("1. Direct status: DOWN\n")
                        unstable("Direct check status is DOWN")

                    } else {
                        echo "Did not found any issues in AvS or Direct. All good"
                        checkStatuses = checkStatuses.concat("1. AvS Status: UP \nDirect status: UP\n")
                        checkInAvsFailed = false
                        checkInDirectFailed = false

                    }
                }
                catch (err) {
                echo err.getMessage()
                unstable("The stage Failed, but it should continue anyway!")
            }
                }
            }
        }


        stage('Checking monitor Directly to make sure it has overall:UP') {
            steps {

                script {
                    try {
                    CHECK_DIRECT = (sh (script: "curl -s --max-time 30 $DIRECT_URL | grep DOWN || echo \"DomainDB is UP\"", returnStdout: true).toString().trim())
                    echo CHECK_DIRECT
                     if (CHECK_DIRECT.toString().contains("DomainDB is UP")) {
                        checkStatuses = checkStatuses.concat("2. Direct check to URL contains \"DomainDB is UP\": OK! \n")


                    } else {
                        checkInDirectFailed = true
                        checkStatuses = checkStatuses.concat("2. Direct check to URL contains \"DOWN\": BAD!\n")
                        unstable("Overall status reported as DOWN from Direct Check")
                }
                }
                catch (err) {
                echo err.getMessage()
                unstable("The stage Failed, but it should continue anyway!")
            }
            }
        }

}
        stage('Checking if there are ThousandEyes alerts for checks 1,2,3 and 8 for') {
            steps {
                script {
                    try {
					echo "$WHERE"
                    CHECK_THOUSAND_EYES = (sh (script: "cat $ENV_JOBS/ThousandEyes/\"$WHERE\".json ", returnStdout: true).toString().trim())
                    echo CHECK_THOUSAND_EYES
                     if (CHECK_THOUSAND_EYES.toString().contains("active")) {
                        checkStatuses = checkStatuses.concat("3. ThousandEyes alert is active for this landscape: BAD \n")
                        unstable("There are active alerts for this landscape")
                        checkInThousandEyes = true


                    } else {
                        
                        checkStatuses = checkStatuses.concat("3. No ThousandEyes alerts active for this landscape: OK \n")

                }
                }
                catch (err) {
                echo err.getMessage()
                unstable("The stage Failed, but it should continue anyway!")
            }
            }
        }

}

        stage('Mail Alert: OUTAGE - Network issues') {
            when {
                expression {

                   checkInAvsFailed == true && checkInDirectFailed == true && checkInThousandEyes == true

                }
            }
            steps {
                script {
                    def bodymail = """

This email is sent as there are tree conditions met:
1. AvS has showed status DOWN
2. Direct check to $DIRECT_URL is down.
3. ThousandEyes has indicated that there are problems with checks 1,2,3 OR 8.


Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER

"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")

                    //if (checkInAvsFailed  && (checkInDirectFailed == true || checkInElkConnsToDomainDB == true || checkInElkDomainDBFailover == true || checkInElkBorrowedConnectionsReached == true || checkInElkUnableToReadUM == true || checkInElkUnexpectedResponse == true || checkInElkDomainDBSlow == true || checkInElkSendToAuditLog == true || checkInElkWriteToAudit == true )) {

                        mail to: toAddress,
                                from: 'igor@mail.sap.hana.ondemand.com',
                                subject: "IGOR: OUTAGE DETECTED (NETWORK)  ${WHERE} $JOB_NAME ${formattedDate} UTC",
                                body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues in DomainDB: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress


                }

            }

        }
       stage("Checking if there was a DomainDB failover.") {


            steps {
                echo "$elkLogStage"
                script {
                    try {
                    CHECK_IN_ELK2 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME2", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK2


                    if (CHECK_IN_ELK2.toString().contains("Exception")) {
                        checkInElkDomainDBFailover = true
                        checkStatuses = checkStatuses.concat("4. Log scanning results DomainDB failover: BAD!\n")
                        unstable("Logs indicated that there was a failover.Check the logs.")
                        //sh(script: "echo \"There was a failover at ${LANDSCAPE} ${checkStatuses}  ${CHECK_IN_ELK2} \" | mail -s \"Failover alert at ${LANDSCAPE} \" -- matthias.gradl@sap.com, valentin.zipf@sap.com")
                    } else {


                        checkStatuses = checkStatuses.concat("4. Log scanning results DomainDB failover: OK!\n")


                    }
                    }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                        }
                }
            }
        }

        stage("Checking DomainDB metrics for CRITICAL") {

            steps {
                echo "$elkLogStage"
                script {
                    try {
                    CHECK_METRICS_1 = sh(script: "cat $ENV_JOBS/CockpitAPI/domaindb.json", returnStdout: true).toString().trim()

                    echo CHECK_METRICS_1
                    if (CHECK_METRICS_1.toString().contains("CRITICAL") || CHECK_METRICS_1.toString().contains("Critical")) {
                        checkInDomainDBMetrics = true
                        checkStatuses = checkStatuses.concat("5. DomainDB metrics contain CRITICAL: BAD!\n")
                        unstable("DomainDB metrics contain CRITICAL")
                    } else {

                        checkStatuses = checkStatuses.concat("5. DomainDB metrics does not contain CRITICAL OK!\n")

                    }
                    }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                        }
                }
            }
        }

        stage("Checking for events with DomainDB in Dynatrace") {

            steps {
                echo "$elkLogStage"
                script {
                    try {
                    CHECK_DYNATRACE = sh(script: "cd $ENV_JOBS/CockpitAPI && jq '.problems[] | select(.tagsOfAffectedEntities[].value==\"domaindb\")' dynatrace.json", returnStdout: true).toString().trim()

                    echo CHECK_DYNATRACE
                    if (CHECK_DYNATRACE.toString().contains("OPEN")) {
                        checkInDynatrace = true
                        checkStatuses = checkStatuses.concat("6. We found some events in Dynatrace: BAD!\n")
                        unstable("There are some events in Dynatrace, master!")
                    } else {

                        checkStatuses = checkStatuses.concat("6. No events in Dynatrace: OK!\n")

                    }
                   }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }
        stage("Checking connectivity service to DomainDB for errors") {

            when {
                expression {
                    (checkInAvsFailed == true)
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    try {
                    CHECK_IN_ELK = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME1", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK
                    if (CHECK_IN_ELK.toString().contains("Could not retrieve tenant form DomainDB")) {
                        checkInElkConnsToDomainDB = true
                        checkStatuses = checkStatuses.concat("7. Log scanning results for Connectivity service to DomainDB: Errors found!\n")
                        unstable("Errors were found. Check the logs for more.")
                    } else {

                        checkStatuses = checkStatuses.concat("7. Log scanning results for Connectivity service to DomainDB: OK!\n")

                    }
                    }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }



        stage("Checking logs for max borrowed connections") {

            when {
                expression {
                    (checkInAvsFailed == true)
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    try {
                    CHECK_IN_ELK3 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME3", returnStdout: true).toString().trim()
                    echo CHECK_IN_ELK3

                    if (CHECK_IN_ELK3.toString().contains("pooled")) {
                        checkInElkBorrowedConnectionsReached = true
                        checkStatuses = checkStatuses.concat("8. Log scanning for max borrowed connections: Errors found!\n")
                        unstable("Logs indicated that all the connections were borrowed.Check the logs.")
                    } else {
                        checkStatuses = checkStatuses.concat("8. Log scanning for max borrowed connections: OK!\n")
                     }
                     }
                     catch (err) {
                     echo err.getMessage()
                     unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        stage("Orchestrator unable to read UM configuration from DomainDB") {

            when {
                expression {
                    (checkInAvsFailed == true)
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    try {
                        CHECK_IN_ELK4 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME4", returnStdout: true).toString().trim()
                        echo CHECK_IN_ELK4

                        if (CHECK_IN_ELK4.toString().contains("ERROR")) {
                            checkInElkUnableToReadUM = true
                            checkStatuses = checkStatuses.concat("9. Log scanning results for Orchestrator reading UM configuration from DomainDB: Errors found!\n")
                            unstable("Error when Orchestrator tries to fetch UM configuration from DomainDB.Check the logs.")
                        } else {
                            checkStatuses = checkStatuses.concat("9. Log scanning results for Orchestrator reading UM configuration from DomainDB: OK!\n")

                        }
                    }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

                stage("Checking Orchestrator logs for faulty/unexpected response from DomainDB") {

            when {
                expression {
                    (checkInAvsFailed == true)
                }
            }
            steps {
                echo "$elkLogStage"
                script {

                        try {
                        CHECK_IN_ELK5 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME5", returnStdout: true).toString().trim()
                        echo CHECK_IN_ELK5

                        if (CHECK_IN_ELK5.toString().contains("ERROR")) {
                            checkInElkUnexpectedResponse = true
                            checkStatuses = checkStatuses.concat("10. Log scanning results unexpected domainDB response: Errors found!\n")
                            unstable("DomainDB has returned 500 to orchestrator.Check the logs.")
                        } else {
                            checkStatuses = checkStatuses.concat("10. Log scanning results for unexpected domainDB response: OK!\n")

                        }
                        }
                        catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }
        stage("Checking Profiling logs for domainDB slowness") {

            when {
                expression {
                    (checkInAvsFailed == true)
                }
            }
            steps {
                echo "$elkLogStage"
                script {

                    try {
                        CHECK_IN_ELK6 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME6", returnStdout: true).toString().trim()
                        echo CHECK_IN_ELK6

                        def checkForDomainDBSlownesResults = (CHECK_IN_ELK6 =~ /system db] finished for \[([0-9]+)sec./)

                        for (i = 0; i < checkForDomainDBSlownesResults.count; i++) {
                            if (checkForDomainDBSlownesResults[i][1].toInteger() > 40) {
                                println checkForDomainDBSlownesResults[i][1]
                                checkStatuses = checkStatuses.concat("11. Log scanning results for DomainDB slowness: BAD!\n")
                                unstable("DomainDB is running slow. Check the Logs for more output on when it said system db] finished for .... " )
                                checkInElkDomainDBSlow = true
                            }
                        }
                        if (CHECK_IN_ELK6.toString().contains("ERROR")) {
                            checkInElkDomainDBSlow = true
                            checkStatuses = checkStatuses.concat("11. Log scanning results for DomainDB slowness: BAD!\n")
                            unstable("Error scanning for deploy scenario failures showed that there are errors.Check the logs.")
                        } else {
                            checkStatuses.concat("8. Pattern matching for log scanning showed no results.\n")
                            echo "Logs not checked or there are no results"


                        }
                        }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                     }
                }
            }
        }
		stage("DomainDB is unable to send messages to Audit Log") {

            when {
                expression {
                    (checkInAvsFailed == true)
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    try {
                        CHECK_IN_ELK7 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME7", returnStdout: true).toString().trim()
                        echo CHECK_IN_ELK7

                        if (CHECK_IN_ELK7.toString().contains("ERROR")) {
                            checkInElkSendToAuditLog = true
                            checkStatuses = checkStatuses.concat("12. DomainDB is unable to send messages to Audit Log: BAD \n")
                            unstable("DomainDB is unable to send messages to AuditLog due to AuditLog failure or network issue. Check the logs.")
                        } else {
                            checkStatuses = checkStatuses.concat("12. DomainDB is unable to send messages to Audit Log: OK!\n")

                        }
                        }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                    }

                }
            }
        }
		stage("DomainDB is unable to write to to Audit Log server") {

            when {
                expression {
                    (checkInAvsFailed == true)
                }
            }
            steps {
                echo "$elkLogStage"
                script {
                    try {
                        CHECK_IN_ELK8 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET2 -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME8", returnStdout: true).toString().trim()
                        echo CHECK_IN_ELK8

                        if (CHECK_IN_ELK8.toString().contains("ERROR")) {
                            checkInElkWriteToAudit = true
                            checkStatuses = checkStatuses.concat("13. DomainDB is unable to write to Audit Log: BAD \n")
                            unstable("DomainDB is not able to write to audit log server. Check the logs.")
                        } else {
                            checkStatuses = checkStatuses.concat("13. DomainDB is able to write to Audit Log: OK!\n")

                        }
                        }
                    catch (err) {
                    echo err.getMessage()
                    unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }
		stage("Mail Alert: Service is down or there is a dependency issue") {

            when {
                expression {
                    (checkInAvsFailed == true && checkInDirectFailed == true) && (checkInDomainDBMetrics == true || checkInDynatrace == true) && (checkInElkDomainDBSlow == true || checkInElkConnsToDomainDB == true || checkInElkDomainDBFailover == true || checkInElkBorrowedConnectionsReached == true || checkInElkUnableToReadUM == true || checkInElkUnexpectedResponse == true || checkInElkSendToAuditLog == true || checkInElkWriteToAudit == true)
                }
            }
           steps {
                script {
                    def bodymail = """


Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER

"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")

                    //if (checkInAvsFailed  && (checkInDirectFailed == true || checkInElkConnsToDomainDB == true || checkInElkDomainDBFailover == true || checkInElkBorrowedConnectionsReached == true || checkInElkUnableToReadUM == true || checkInElkUnexpectedResponse == true || checkInElkDomainDBSlow == true || checkInElkSendToAuditLog == true || checkInElkWriteToAudit == true )) {

                        mail to: toAddress,
                                from: 'igor@mail.sap.hana.ondemand.com',
                                subject: "IGOR: OUTAGE DETECTED (Service Or Dependency) ${WHERE} DOMAINDB issues detected at:  ${formattedDate} UTC",
                                body: "Hi Colleagues,\n\nThere was a failover at $WHERE landscape. Please check the report:  \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddressFailover

                   //}
                }

            }
            }
		stage("Send mail to DomainDB colleagues for failover") {

            when {
                expression {
                    (checkInElkDomainDBFailover == true)
                }
            }
           steps {
                script {
                    def bodymail = """


Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER

"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")

                    //if (checkInAvsFailed  && (checkInDirectFailed == true || checkInElkConnsToDomainDB == true || checkInElkDomainDBFailover == true || checkInElkBorrowedConnectionsReached == true || checkInElkUnableToReadUM == true || checkInElkUnexpectedResponse == true || checkInElkDomainDBSlow == true || checkInElkSendToAuditLog == true || checkInElkWriteToAudit == true )) {

                        mail to: toAddress,
                                from: 'igor@mail.sap.hana.ondemand.com',
                                subject: "IGOR: OUTAGE DETECTED ${WHERE} DOMAINDB issues detected at:  ${formattedDate} UTC",
                                body: "Hi Colleagues,\n\nThere was a failover at $WHERE landscape. Please check the report:  \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddressFailover

                   //}
                }

            }
            }


     stage("Checking for failed stages") {
            steps {
                script {
                    CHECK_FAILURES = sh(script: "curl  ${BUILD_URL}/consoleText", returnStdout: true).toString().trim()
                    echo CHECK_FAILURES
                     if (CHECK_FAILURES.toString().contains("The stage Failed"))  {


                                 def bodymail = """

Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")
                        mail to: toAddress,
                                from: 'igor@mail.sap.hana.ondemand.com',
                                subject: "IGOR: Pipeline is failing: ${JOB_NAME}  ${formattedDate} UTC Build: $BUILD_NUMBER",
                                body: "The Pipeline has failed. Please check all the UNSTABLE/Yellow stages to see why:"  + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress

                    } else {

                        echo "No Failing stages found"

                    }

             }

         }

     }


        stage('Send Mail Alert') {
            when {
                expression {
                    checkInElkDomainDBFailover == true  || (checkInAvsFailed && (checkInDirectFailed == true || checkInDynatrace == true || checkInDomainDBMetrics == true || checkInElkConnsToDomainDB == true  || checkInElkBorrowedConnectionsReached == true  || checkInElkUnableToReadUM == true  || checkInElkUnexpectedResponse == true  || checkInElkDomainDBSlow == true  || checkInElkSendToAuditLog == true  || checkInElkWriteToAudit == true  ))

                }
            }
            steps {
                script {
                    def bodymail = """


The pipeline consists of the following stages:
1. AvS check for Start scenario - UP/DOWN + direct requests if the AvS status is down.

2. Direct check to URL in $LANDSCAPE to make sure the response does not contain "DOWN"

3. Check in ThousandEyes to see if there are alerts for checks 1,2,3 or 8 (inbound and outbound tests)

4. Checking for DomainDB Failover. If present, this will mean that there was a failover done recently. Check if the new DB is working.
account: ngjpinfra AND component: domaindb AND "at com.sybase.jdbc4.jdbc.SybConnection.handleHAFailover"

5. Checking DomainDB App and DB for CRITICAL metrics.

6. Checking in Dynatrace to see if there are open problems for domainDB.

7. Checking Connectivity service to DomainDB to see if there are any logs in Connectivity service malfunctioning due to DomainDB
"Could not retrieve tenant form DomainDB" AND component: connectivitycertificate account: ngjpinfra NOT 404 NOT 400

8. DomainDB_Database_issues with Pooled connections. This will show if all the pooled connections were borrowed.
pooled  AND component: domaindb account: ngjpinfra

9. Orchestrator unable to read UM configuration from DomainDB
"An error occured while reading UM service configuration from DomainDB "  AND component: orchestrator AND file_name: ljs_trace

10. Check in Orchestrator logs for any malfunctioning due to DomainDB issues.
"Unexpected response" AND component: orchestrator AND file_name: ljs_trace

11. Checking Profiling logs of Orchestrator for domainDB slowness. Treshold is 40 seconds.
*com.sap.core.internal.deploy.service* AND (testdeployscenario* OR runtimetestcanary*) AND ("finished for" OR "Create application metadata" ) AND file_name:profiling

12. DomainDB is unable to send messages to Audit log service.
"AuditLogWriteException: Unable to send the auditlog message to the auditlog server." AND component: domaindb account: ngjpinfra

13. DomainDB is unable to write to Audit Log service.
"Cannot write to audit log" AND component: domaindb account: ngjpinfra


Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER

"""
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")

                    //if (checkInAvsFailed  && (checkInDirectFailed == true || checkInElkConnsToDomainDB == true || checkInElkDomainDBFailover == true || checkInElkBorrowedConnectionsReached == true || checkInElkUnableToReadUM == true || checkInElkUnexpectedResponse == true || checkInElkDomainDBSlow == true || checkInElkSendToAuditLog == true || checkInElkWriteToAudit == true )) {

                        mail to: toAddress,
                                from: 'igor@mail.sap.hana.ondemand.com',
                                subject: "IGOR: ${WHERE} DOMAINDB issues detected at:  ${formattedDate} UTC",
                                body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues in DomainDB: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress

                   //}
                }

            }

        }
    }
}
