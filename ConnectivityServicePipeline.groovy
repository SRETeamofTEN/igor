def checkStatuses = "".toString();
def toAddress = "DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()

//def toAddress = "jane.savova@sap.com".toString()

pipeline {
    options {
        timeout(time: 10, unit: 'MINUTES')
    }
    agent any
    environment {
        checkInAvsFailed = false
        checkInAvsFailed2 = false
        checkInElkFailed = false
        checkInDirectFailed = false
        checkInDirectFailed2 = false
        checkInMetrics = false
        checkInMetricsConnPing = false
        checkInThousandEyes = false
        checkAuditLogBuild = false
        checkDomainDBLastBuild = false

        //python variables
        EVAL_DATA = '$ENV_EVAL_DATA'
        DIRECT_URL = '$ENV_DIRECT_URL'

        EVAL_DATA2 = '$ENV_EVAL_DATA2'
        DIRECT_URL2 = '$ENV_DIRECT_URL2'
		
		AUDITLOG_PIPELINE = '$ENV_AUDITLOG_PIPELINE'
		DOMAINDB_PIPELINE = '$ENV_DOMAINDB_PIPELINE'

        //Java for Variables General Deploy issues
        CHECK_NAME = 'DEPLOY_OPERATION'
        DC = '$WHERE'
        AUTH = credentials("puser")
        TARGET = 'ELK'  //could be LOG or ELK depending on where to search
        TARGET2 = 'LOG'
        elkLogStage = "Check Logs for $TARGET"

        //Java Variables for Check 2 and 3 about profiling logs.

        CHECK_NAME9 = 'CONNSTODOMAINDB'

    }

    stages {
        
        stage('CLEAN') {
            failFast true
            steps {
                cleanWs()
            }
        }

        stage('AvS monitor and Direct for Connectivity Services - Java') {
            steps {

                script {
                    try {
                        CHECK_IN_AVS = sh(script: "python $ENV_JOBS/avs_requests.py $EVAL_DATA $DIRECT_URL", returnStdout: true).toString().trim()
                        echo CHECK_IN_AVS
                        if (CHECK_IN_AVS.toString().contains("Down")) {
                            checkInAvsFailed = true
                            checkStatuses = checkStatuses.concat("1. AvS status of Connectivity JAVA app: DOWN\n")
                            unstable("Status in AVS is DOWN")
                        } else {

                            checkStatuses = checkStatuses.concat("1. AvS status of Connectivity JAVA app: UP\n")
                        }

                        if (CHECK_IN_AVS.toString().contains("500")) {

                            echo "Looking for 500"
                            checkInDirectFailed = true
                            checkStatuses = checkStatuses.concat("1. Direct check status of Connectivity JAVA app: DOWN\n")
                            unstable("Direct check status is DOWN with 500")

                        } else {
                            echo "Did not found any issues in AvS for Connectivity Service JAVA. All good"
                            checkStatuses = checkStatuses.concat("1. Direct check status of Connectivity JAVA app: UP\n")
                            checkInDirectFailed = false

                        }
                    } catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }

        }

        stage('AvS monitor and Direct for Connectivity Services - HTML5') {
            steps {
                script {
                    try {
                        CHECK_IN_AVS2 = sh(script: "python $ENV_JOBS/avs_requests.py $EVAL_DATA2 $DIRECT_URL2", returnStdout: true).toString().trim()
                        echo CHECK_IN_AVS2
                        if (CHECK_IN_AVS2.toString().contains("DOWN")) {
                            checkInAvsFailed2 = true
                            checkStatuses = checkStatuses.concat("2. AvS status of HTML5 app: DOWN\n")
                            unstable("Check status in AVS is DOWN")


                        } else {

                            checkStatuses = checkStatuses.concat("2. AvS status of HTML5 app: UP\n")

                        }

                        if (CHECK_IN_AVS2.toString().contains("500")) {

                            echo "Looking for 500"
                            checkInDirectFailed2 = true
                            checkStatuses = checkStatuses.concat("2. Direct check of status of HTML5 app: DOWN\n")
                            unstable("Direct check status is DOWN")


                        } else {
                            echo "Did not found any issues in AvS or Direct. All good for Connectivity Service HTML5"
                            checkStatuses = checkStatuses.concat("2. Direct check of status of HTML5 app: UP\n")
                            checkInDirectFailed2 = false

                        }
                    } catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        stage('Network check - ThousandEyes alerts for checks 1,2,3 and 8') {
            when {
                expression {
                    (checkInAvsFailed == true || checkInAvsFailed2 == true)

                }
            }
            steps {
                script {
                    try {
                        CHECK_THOUSAND_EYES = (sh(script: "cat $ENV_JOBS/ThousandEyes/\"$WHERE\".json ", returnStdout: true).toString().trim())
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
                    (checkInAvsFailed == true && checkInDirectFailed == true && checkInThousandEyes == true) || (checkInAvsFailed2 == true && checkInDirectFailed2 == true && checkInThousandEyes == true)

                }
            }
            steps {
                script {
                    def bodymail = """

This email is sent as there are tree conditions met:
1. AvS has showed status DOWN
2. Direct check to $DIRECT_URL is down or $DIRECT_URL2 is down.
3. ThousandEyes has indicated that there are problems with checks 1,2,3 OR 8.

Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER



        """
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")


                    mail to: toAddress,
                            from: 'igor@mail.sap.hana.ondemand.com',
                            subject: "IGOR: NETWORK OUTAGE DETECTED ${WHERE} Connectivity Service issues detected at:  ${formattedDate} UTC",
                            body: "Hi Colleagues,\nHere is an automated report from Igor pipeline that caught some issues in the Network and Connectivity Service is affected: \n\n" + checkStatuses + bodymail + "\n" + "\nRegards, \nIgor"
                    echo "Mail Sent to " + toAddress


                }

            }

        }


        stage('Check Audit Log service - pipeline last build') {
            when {
                expression {
                    checkInAvsFailed == true || checkInAvsFailed2 == true

                }
            }
            steps {
                script {
                    try {

                        CHECK_AUDIT_LOG_LAST_BUILD = sh(script: "curl -s $AUDITLOG_PIPELINE | jq '.result'", returnStdout: true).toString().trim()

                        if (CHECK_AUDIT_LOG_LAST_BUILD.toString().contains("UNSTABLE") || CHECK_AUDIT_LOG_LAST_BUILD.toString().contains("FAILED")) {
                            checkAuditLogBuild = true
                            checkStatuses = checkStatuses.concat("4. Last build of Audit Log service pipeline check: UNSTABLE/FAILED\n")
                            unstable("Last build of Audit log was UNSTABLE")
                        } else {

                            checkStatuses = checkStatuses.concat("4. Last build of Audit Log service pipeline check: STABLE\n")
                        }

                    } catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }

        }


        stage("Connectivity errors toward DomainDB") {
            //Check if there are connectivity issues between connectivity service and Domain DB error is  "Could not retrieve tenant form DomainDB AND component: connectivitycertificate account: ngjpinfra NOT 404 NOT 400"
            when {
                expression {
                    checkInAvsFailed == true || checkInAvsFailed2 == true

                }
            }

            steps {

                script {
                    try {
                        CHECK_DOMAINDB_LAST_BUILD = sh(script: "curl -s $DOMAINDB_PIPELINE | jq '.result'",returnStdout: true).toString().trim()


                        if (CHECK_DOMAINDB_LAST_BUILD.toString().contains("UNSTABLE")||CHECK_DOMAINDB_LAST_BUILD.toString().contains("FAILED")) {
                            checkDomainDBLastBuild = true
                            checkStatuses = checkStatuses.concat("5. Last build for domainDB seems to have failed. Please do check logs for more info.\n")
                            unstable("Last Build of AuditLog is Unstable or Failed.Check the logs.")
                           
                        } else {
                           
                            echo "Last DomainDB build was sucessfull. Continuing."
                            checkStatuses = checkStatuses.concat("5. Pattern matching for log scanning showed no results. Log scanning results for general problems from connection services to DomainDB issues: No errors were found!\n")

                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }

                }

            }
        }
        stage("Check connectivity service machines (static hosts) for CRITICAL metric") {
            when {
                expression {
                    checkInAvsFailed == true || checkInAvsFailed2 == true

                }
            }
            steps {
                script {
                    try {
                        CHECK_METRICS_CONNECTION = sh(script: "cat $ENV_JOBS/CockpitAPI/connectivity.json", returnStdout: true).toString().trim()

                        echo CHECK_METRICS_CONNECTION
                        if (CHECK_METRICS_CONNECTION.toString().contains("CRITICAL") || CHECK_METRICS_CONNECTION.toString().contains("Critical")) {
                            checkInMetrics = true
                            checkStatuses = checkStatuses.concat("6. Connectivity Service machines contains CRITICAL: BAD!\n")
                            unstable("Connectivity vsa metrics contain CRITICAL. ")
                        } else {
                            echo "Connectivity vsa metrics are OK."
                            checkStatuses = checkStatuses.concat("6. Connectivity metrics does not contain CRITICAL: OK! \n")
                        }
                    } catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        stage("ConnectivityPing virtual machine metrics") {
            //execute only if the check via AVS towards the connectivityPing app fails. Sometimes when the connectivityPing check fails could mean that only the check is failing due to issues with the machine where the app is running, but overall the service is stable and in operation
            when {
                expression {
                    checkInAvsFailed == true || checkInAvsFailed2 == true

                }
            }
            steps {
                script {
                    try {
                        CHECK_METRICS_VSA = sh(script: "cat $ENV_JOBS/CockpitAPI/conn_list.json", returnStdout: true).toString().trim()

                        echo CHECK_METRICS_VSA
                        if (CHECK_METRICS_VSA.toString().contains("CRITICAL") || CHECK_METRICS_VSA.toString().contains("Critical")) {
                            checkInMetricsConnPing = true
                            checkStatuses = checkStatuses.concat("7. ConnectivityPING VSA contains CRITICAL: BAD! Check logs and VSA stats!!!\n")
                            unstable("ConnectivityPING VSA metrics contain CRITICAL. ")
                        } else {
                            echo "ConnectivityPING vsa metrics are OK. All green!"
                            checkStatuses = checkStatuses.concat("7. ConnectivityPING vsa metrics does not contain CRITICAL: OK! \n")
                        }
                    } catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }

            }

        }

        stage("Checking for events with Connectivity in Dynatrace") {

            steps {

                script {
                    try {
                        CHECK_DYNATRACE = sh(script: "jq '.problems[] | select(.tagsOfAffectedEntities==\"ConnectivityAgent\" or .tagsOfAffectedEntities==\"ConnectivityDirectAgent\")' $ENV_JOBS/CockpitAPI/dynatrace.json", returnStdout: true).toString().trim()

                        echo CHECK_DYNATRACE
                        if (CHECK_DYNATRACE.toString().contains("OPEN")) {
                            checkInDynatrace = true
                            checkStatuses = checkStatuses.concat("8. We found some events in Dynatrace: BAD!\n")
                            unstable("There are some events in Dynatrace, master!")
                        } else {
                            checkStatuses = checkStatuses.concat("8. No events in Dynatrace: OK!\n")
                        }
                    } catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
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
        stage('Mail Alert: OUTAGE - Service issues') {
            when {
                expression {
                    //checkInAvsFailed == true && (checkInAvsFailed2 == true || CHECK_AUDIT_LOG_LAST_BUILD == true || checkInMetrics == true || checkInDirectFailed == true || checkInDirectFailed2 == true || checkInThousandEyes == true)
                    ((checkInAvsFailed == true && checkInDirectFailed == true) || (checkInAvsFailed2 == true && checkInDirectFailed2 == true)) && (checkAuditLogBuild == true || checkDomainDBLastBuild == true || checkInMetrics == true || checkInThousandEyes == true || checkInMetricsConnPing == true)
                }
            }
            steps {
                script {
                    def bodymail = """



The pipeline consists of the following stages:

1. AvS monitor and Direct for Connectivity Services - Java - performs a check of the connectivity app from AVS and directly.
2. AvS monitor and Direct for Connectivity Services - HTML5 - performs a check of the connectivity app from AVS and directly.
3. Network check - ThousandEyes alerts for checks 1,2,3 and 8 for $WHERE - performs a network check from ThousandEyes ONLY IF check 1 or 2 fail
4. Check Audit Log service availability - as AuditLog is dependency to ConnectivityService we check if the build of the Audit Log pipeline check finished successfully or not
5. Connectivity errors toward DomainDB  - as DomainDB is dependency to ConnectivityService we check for errors in it ONLY IF checks 1 and 2 fail
6. Check connectivity service machines (static hosts) for CRITICAL metric - If checks 1 and 2 fail we check the ConnectivityService Static Hosts for any critical metrics
7. ConnectivityPing vsa metrics - If checks 1 and 2 fail we are obliged to check the VSA on which the connectivityPing app run if there are any Critical Metrics. It could happen that the vsa where the connectivityPing app does not run, but the ConnectivityService is fine and working
8. Checking for events with Connectivity in Dynatrace - check if there are any events for connectivity service in Dynatrace
9. Email is generated when the build is unstable.

Job Name is: $JOB_NAME
Job URL: $JOB_URL
Job Console Output (in case of issues with the build itself):
$JOB_URL$BUILD_NUMBER/console
Check build number: $BUILD_NUMBER

        """
                    def date = new Date()
                    def formattedDate = date.format("MMMM dd HH:mm")

                    mail to: toAddress,
                            from: 'igor@mail.sap.hana.ondemand.com',
                            subject: "IGOR: OUTAGE DETECTED ${WHERE} Connectivity Service issues detected at:  ${formattedDate} UTC",
                            body: "Hi Colleagues,\nHere is an automated report from Igor pipeline that caught some issues in the Connectivity Service: \n\n" + checkStatuses + bodymail + "\n" + "\nRegards, \nIgor"
                    echo "Mail Sent to " + toAddress


                }

            }


        }
    }
}
