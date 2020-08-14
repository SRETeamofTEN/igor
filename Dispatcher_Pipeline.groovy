def checkStatuses = "".toString();
def toAddress = "DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()
//def toAddress = "hristo.popov@sap.com, boyan.tomov@sap.com".toString()

pipeline {
    agent any
    environment {


        checkInRuntimeAvsFailed = false;
        checkInRuntimeDirectFailed = false
        checkDispatchersHealth = false
        checkDispatchersHealthAVS = false

        checkVMCount = false
        checkInProcessState= false
        checkInMetricState = false
        checkInDesignTimeAvS = false
        checkInDesignTimeDirectFailed = false
        checkInDesignTimeBusyThreads = false
        checkInDispatcherDB = false
        checkInDispatcherDBPooledConnections = false
        checkGitBT = false


        //python variables
        EVAL_DATA_RUNTIME = '$ENV_EVAL_DATA_RUNTIME'
        EVAL_DATA_DESIGNTIME = '$ENV_EVAL_DATA_DESIGNTIME'
        EVAL_DATA_VM_COUNT = '$ENV_EVAL_DATA_VM_COUNT'

        DIRECT_URL_RUNTIME = '$ENV_DIRECT_URL_RUNTIME'
        DIRECT_URL_DESIGNTIME = '$ENV_DIRECT_URL_DESIGNTIME'
        DIRECT_URL_VM_COUNT = '$ENV_DIRECT_URL_VM_COUNT'

        //DISPATCHER_HEALTHCHECK_URL = '$ENV_DISPATCHER_HEALTHCHECK_URL'
        //DISPATCHER_COUNT_FOR_LANDSCAPE = '$ENV_DISPATCHER_COUNT_FOR_LANDSCAPE'

        //Java for Variables General Deploy issues

        DC = '$WHERE'
        AUTH = credentials("puser")
        //USERNAME = '$sshUser'
        //PASSWORD = '$sshPass'

        TARGET = 'ELK'  //could be LOG or ELK depending on where to search
        TARGET2 = 'LOG'  //could be LOG or ELK depending on where to search
        elkLogStage = "Check Logs for $TARGET"

        //Java Variables for Check 2 and 3 about profiling logs.
        CHECK_NAME1 = 'DISPATCHERDBISSUES'
        CHECK_NAME2 = 'DISPATCHER_POOLED_CONNECTIONS'


    }

    stages {
        stage('Checking monitor in AvS and Directly (Runtime Monitor)') {
            steps {
                script {
                    try {
                        CHECK_IN_AVS = sh(script: "python $ENV_JOBS/avs_requests.py $EVAL_DATA_RUNTIME $DIRECT_URL_RUNTIME", returnStdout: true).toString().trim()
                        echo CHECK_IN_AVS
                        if (CHECK_IN_AVS.toString().contains("DOWN")) {
                            checkInRuntimeAvsFailed = true
                            checkStatuses = checkStatuses.concat("1. AvS status for Runtime: DOWN\n")
                            unstable("Check status for Runtime in AVS is DOWN")
                        }

                        else {

                            checkStatuses = checkStatuses.concat("1. AvS status for Runtime: UP\n")
                        }

                        if (CHECK_IN_AVS.contains("unavailable") || CHECK_IN_AVS.contains("down") || CHECK_IN_AVS.contains("not available")) {

                            checkInRuntimeDirectFailed = true
                            checkStatuses = checkStatuses.concat("2. Direct status for Runtime: DOWN\n")
                            unstable("Direct check status for Runtime is DOWN")

                        } else {
                            echo "Did not found any issues in AvS or Direct for HTML5 Runtime. All good"
                            checkStatuses = checkStatuses.concat("2. Direct status for Runtime: UP\n")
                            checkInRuntimeDirectFailed = false
                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }



        stage('Checking AvS Monitor for Dispatcher Health(DesignTime)"') {
            steps {
                script {
                    try {
                        CHECK_IN_AVS_DISPATCHER_HEALTH = sh(script: "python $ENV_JOBS/avs_requests.py $EVAL_DATA_DESIGNTIME $DIRECT_URL_DESIGNTIME", returnStdout: true).toString().trim()
                        echo CHECK_IN_AVS_DISPATCHER_HEALTH
                        if (CHECK_IN_AVS_DISPATCHER_HEALTH.toString().contains("DOWN") || CHECK_IN_AVS_DISPATCHER_HEALTH.toString().contains("500")) {
                            checkStatuses = checkStatuses.concat("3. DesignTime AvS/Direct status: BAD! \n")
                            checkInDesignTimeAvS = true
                            checkInDesignTimeDirectFailed = true
                            unstable("Some of the dispatcher VM's are reported as not healthy: BAD!.")
                        }
                        else {

                            checkStatuses = checkStatuses.concat("3. DesignTime AvS/Direct status: OK!\n")
                            checkInDesignTimeAvS = false
                            checkInDesignTimeDirectFailed = false
                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        /*   stage("Checking directly if dispatcher nodes are healthy(DesignTime)") {
  
              when {
                  expression {
                      checkInDesignTimeAvS == true
                  }
              }
              steps {
  
                  script {
                      try {
                          CHECK_IF_DISPATCHERS_HEALTHY= sh(script: "curl -s -u $AUTH_USR:$AUTH_PSW  $DIRECT_URL_VM_COUNT | python -m json.tool | egrep -iw 'healthy|name' | grep -v \"Application state is NOT OK\" ", returnStdout: true).toString().trim()
                          echo CHECK_IF_DISPATCHERS_HEALTHY
                          if (CHECK_IF_DISPATCHERS_HEALTHY.toString().contains("false")) {
                              checkDispatchersHealth = true
                              checkStatuses = checkStatuses.concat("4. Some of the dispatcher VM's are reported as not healthy: BAD!\n")
                              unstable("Some of the dispatcher VM's are reported as not healthy: BAD!.")
                          } else {
  
                              checkStatuses = checkStatuses.concat("4. Direct check if dispatchers are healthy: OK!\n")
                              checkDispatchersHealth = false
                          }
                      }
                      catch (err) {
                          echo err.getMessage()
                          unstable("The stage Failed, but it should continue anyway!")
                      }
                  }
              }
          }
  
              stage("Checking Dispatcher VMs count") {
                   steps {
  
                       script {
                           try {
                               DISPATCHER_COUNT= sh(script: "curl -s -u $AUTH_USR:$AUTH_PSW  $DIRECT_URL_VM_COUNT | python -m json.tool | egrep -iw 'name' | wc -l", returnStdout: true).toString().trim()
                               echo DISPATCHER_COUNT
                               if (DISPATCHER_COUNT.toInteger() < "$ENV_DISPATCHER_COUNT_FOR_LANDSCAPE".toInteger() ) {
                                   checkVMCount = true
                                   checkStatuses = checkStatuses.concat("5. Dispatcher count: BAD\n")
                                   unstable("Dispatcher count is less than expected")
                               } else {
                                   checkStatuses = checkStatuses.concat("5. Dispatcher count is as expected or higher: $DISPATCHER_COUNT - OK! \n")
                               }
                           }
                           catch (err) {
                               echo err.getMessage()
                               unstable("The stage Failed, but it should continue anyway!")
                           }
                       }
                   }
               }
       */

        stage('Checking STATE of application in Monitoring API"') {
            when {
                expression {
                    ((checkInRuntimeAvsFailed == true && checkInRuntimeDirectFailed == true) || (checkInDesignTimeAvS == true && checkInDesignTimeDirectFailed == true))
                }
            }
            steps {

                script {
                    try {
                        CHECK_STATE_IN_MONITORING = sh(script: "python $ENV_JOBS/getStateAPI.py services dispatcher   https://api.hana.ondemand.com/monitoring/v1/accounts/ state", returnStdout: true).toString().trim()
                        echo CHECK_STATE_IN_MONITORING
                        if (CHECK_STATE_IN_MONITORING.toString().contains("CRITICAL") || CHECK_STATE_IN_MONITORING.toString().contains("UNKNOWN") || CHECK_STATE_IN_MONITORING.toString().contains("STALE")) {
                            checkStatuses = checkStatuses.concat("6. Dispatcher Processes State: BAD! \n")
                            checkInProcessState = true
                            unstable("Some of the dispatcher VM's have critical State: BAD!.")
                        } else {
                            checkStatuses = checkStatuses.concat("6. Dispatcher Processes State: OK!\n")
                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        stage('Checking METRICS of application in Monitoring API"') {
            when {
                expression {
                    ((checkInRuntimeAvsFailed == true && checkInRuntimeDirectFailed == true) || (checkInDesignTimeAvS == true && checkInDesignTimeDirectFailed == true))
                }
            }
            steps {

                script {
                    try {
                        CHECK_METRICS_IN_MONITORING = sh(script: "python $ENV_JOBS/getStateAPI.py services dispatcher   https://api.hana.ondemand.com/monitoring/v1/accounts/ metrics", returnStdout: true).toString().trim()
                        echo CHECK_METRICS_IN_MONITORING
                        if (CHECK_METRICS_IN_MONITORING.toString().contains("CRITICAL") || CHECK_METRICS_IN_MONITORING.toString().contains("WARNING")) {
                            checkStatuses = checkStatuses.concat("7. Dispatcher metrics: BAD! \n")
                            checkInProcessState = true
                            unstable("Some of the dispatcher VM's have critical metrics: BAD!.")
                        } else {
                            checkStatuses = checkStatuses.concat("7. Dispatcher metrics: OK!\n")
                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        stage("Checking logs for DB issues") {

            steps {

                script {
                    try {
                        CHECK_IN_ELK = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME1", returnStdout: true).toString().trim()
                        echo CHECK_IN_ELK


                        if (CHECK_IN_ELK.toString().contains("ERROR")) {
                            checkInDispatcherDB = true
                            checkStatuses = checkStatuses.concat("8. Log scanning for Dispatcher DB issues: Errors found! \n")
                            unstable("There are errors with Dispatcher DB. Check the logs.")
                        } else {
                            checkStatuses = checkStatuses.concat("8. Log scanning for Dispatcher DB issues: OK!\n")
                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }
        stage("Checking logs for max borrowed connections to DB") {

            steps {

                script {
                    try {
                        CHECK_IN_ELK2 = sh(script: "cd $ENV_JOBS/ && java -jar $ENV_JOBS/KibanaSearch.jar -l $LANDSCAPE -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME2", returnStdout: true).toString().trim()
                        echo CHECK_IN_ELK2


                        if (CHECK_IN_ELK2.toString().contains("ERROR") || CHECK_IN_ELK2.toString().contains("pooled") || CHECK_IN_ELK2.toString().contains("borrowed")) {
                            checkInDispatcherDBPooledConnections = true
                            checkStatuses = checkStatuses.concat("9. Log scanning for Dispatcher max borrowed connections: Errors found! \n")
                            unstable("There are errors with Dispatcher DB. Check the logs.")
                        } else {
                            checkStatuses = checkStatuses.concat("9. Log scanning for Dispatcher max borrowed connections: OK!\n")
                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }

        stage("Checking DesignTimeBusyTreads") {

            steps {

                script {
                    try {
                        DT_BUSY_THREADS = sh(script: "cd $ENV_JOBS/ && python getStateAPI.py services dispatcher https://api.hana.ondemand.com/monitoring/v1/accounts/ metrics | egrep -iw 'DesignTimeBusyThreads = [0-9]+' | awk -F \" = \" {'print \$2'}", returnStdout: true).toString().trim()

                        echo DT_BUSY_THREADS

                        def checkDesignTime = (DT_BUSY_THREADS =~ /([0-9]+)/)

                        for (i = 0; i < checkDesignTime.count; i++) {
                            if (checkDesignTime[i][1].toInteger() > 160) {
                                println checkDesignTime[i][1]
                                checkStatuses = checkStatuses.concat("10. DesignTimeBusyThreads has spiked: BAD!\n")
                                unstable("Too many design time busy threads " )
                                checkInDesignTimeBusyThreads = true
                            }
                            else {
                                checkStatuses = checkStatuses.concat("10. DesignTimeBusyThreads: OK!\n")
                                break;
                            }
                        }
                    }
                    catch (err) {
                        echo err.getMessage()
                        unstable("The stage Failed, but it should continue anyway!")
                    }
                }
            }
        }
        stage("Checking GIT as DesignTimeBT has spiked") {
            when {
                expression {
                    checkInDesignTimeBusyThreads == true
                }
            }
            steps {

                script {
                    try {
                        CHECK_GIT = sh(script: "cd $ENV_JOBS/ && python getStateAPI.py services git  https://api.hana.ondemand.com/monitoring/v1/accounts/ metrics | egrep -iw 'currentThreadsBusy = [0-9]+' | awk -F \" = \"  {'print \$2'} | awk {'print \$1'}", returnStdout: true).toString().trim()
                        echo CHECK_GIT
                        def checkGIT_BT = (CHECK_GIT =~ /([0-9]+)/)
                        for (i = 0; i < checkGIT_BT.count; i++) {
                            if (checkGIT_BT[i][1].toInteger() > 180) {
                                println checkGIT_BT[i][1]
                                checkStatuses = checkStatuses.concat("11. GIT: My Busy Threads have Spiked, sorry. Please restart me :( : BAD!\n")
                                unstable("Too many busy threads " )
                                checkGitBT = true
                            }

                            else {
                                checkStatuses = checkStatuses.concat("11. GIT BusyThreads: OK!\n")
                            }
                        }
                    }
                    catch (err) {
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
        stage('Send Mail Alert') {
            when {
                expression {
                    //true
                    ((checkInRuntimeAvsFailed == true && checkInRuntimeDirectFailed == true) || (checkInDesignTimeAvS == true && checkInDesignTimeDirectFailed == true))
                }
            }
            steps {
                script {
                    def bodymail = """

1. Checking Dispatcher Runtime in AvS and Directly to URL in Definition
2. Checking Dispatcher Runtime Directly
3. Checking Dispatcher DesignTime in AvS and Dispatcher DesignTime Directly
4. Checking directly if dispatcher nodes are healthy (should return 'healthy:true') - runs when 1 and 2 or 3 and 4 fails
5. Getting the number of Dispatcher's Running Processes
6. Checking Dipsatcher's State via the Monitoring API - runs when 1 and 2 or 3 and 4 fails
7. Checking Dipsatcher's Metrics (CPU, Memory, Disk, Requests and etc.) - runs when 1 and 2 or 3 and 4 fails
8. Checking Kibana's Logs for Dispatcher DB Errors
"Internal error: Exception" AND "Eclipse Persistence Services" AND account:services AND application:dispatcher AND file_name:ljs_trace
9. Checking Kibana's Logs for Max Borrowed Connections
"has no pooled/idle connections available for this request" OR "0 Pooled/idle connection") AND account: services AND application:dispatcher AND file_name: ljs_trace
10. Checking Dispatcher DesignTime for BusyThreads (threshold is 160)
11. Checking GIT for BusyThreads (threshold is 180) - runs when 10 is above the threshold
12. Send email alert with detailed report.


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
                            subject: "IGOR:${WHERE} Dispatcher issues detected at: ${formattedDate} UTC Build: $BUILD_NUMBER",
                            body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues with Dispatcher: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                    echo "Mail Sent to " + toAddress


                }

            }

        }
    }
}

