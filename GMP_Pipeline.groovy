def checkStatuses = "".toString();
def toAddress = "DL_59BA2AAB5F99B79157000011@exchange.sap.corp".toString()

JOBS_PATH = '$ENV_JOBS_PATH'
COCKPITAPI_PATH = '$ENV_COCKPITAPI_PATH'
THOUSAND_EYES_PATH = '$ENV_THOUSAND_EYES'

pipeline {
	options {
      timeout(time: 10, unit: 'MINUTES')
	}

    agent any
    environment {

        JOBS_PATH = '$ENV_JOBS_PATH'
        COCKPITAPI_PATH = '$ENV_COCKPITAPI_PATH'
        THOUSAND_EYES_PATH = '$ENV_THOUSAND_EYES'
        checkInElkNoHV = false
        checkPools = false
        checkInElkNoVM = false
        checkInElkVMCreateFailed = false
        checkInElkVMCreateTimeout = false
        checkInElkPoolSizeMaintainer = false
        checkInElkRepoFailed = false
        checkInElkConnectionResets = false
        checkInElkGMPConnectionIssues = false
        //Java for Variables

        DC = '$WHERE'
		AUTH = credentials("puser")

        TARGET = 'ELK'  //could be LOG or ELK depending on where to search
        elkLogStage = "Check Logs for $TARGET"


        CHECK_NAME1 = 'GMP_NO_HV'
        CHECK_NAME2 = 'GMP_NO_VM'
        CHECK_NAME3 = 'GMP_VM_CREATE_FAILED'
        CHECK_NAME4 = 'GMP_VM_CREATE_TIMEOUT'
        CHECK_NAME5 = 'REPO50K_ISSUE'
        CHECK_NAME6 = 'GMP_POOL_SIZE_MAINTAINER'
        //CHECK_NAME7 = 'GMP_CONNECTION_RESET'
        CHECK_NAME8 = 'GMP_CONNECTION_ISSUES'



    }

    stages {
        stage('Check if there are CRITICAL Availability zones or pools') {
            steps {
                script {
					try {
					echo JOBS_PATH
					echo COCKPITAPI_PATH
					echo THOUSAND_EYES_PATH

						GMP_POOLS_CHECK = sh(script:" jq -c '.cloudControllers[] | { Status: .availabilityZone.status, Pool:  .availabilityZone.name, InstanseSize: .instanceSize}' $COCKPITAPI_PATHmetrics.txt ", returnStdout: true).toString().trim()
						echo GMP_POOLS_CHECK
						if (GMP_POOLS_CHECK.toString().contains("CRITICAL")  || GMP_POOLS_CHECK.toString().contains("Critical")) {
							checkPools = true
							checkStatuses = checkStatuses.concat("1. Metrics for GMP pools show that there are critical pools:BAD\n")
							unstable("There are pools in CRITICAL STATE")
						} else {
							checkStatuses = checkStatuses.concat("1. No pools in CRITICAL state: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }

        stage('Checking Logs for HV capacity issues') {
            when {
                expression {
                    checkPools == true
                }
            }
            steps {
                script {
					try {
						CHECK_IN_ELK = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME1", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK
						if (CHECK_IN_ELK.toString().contains("No suitable hypervisors found")) {
							checkInElkNoHV = true
							checkStatuses = checkStatuses.concat("1. Log scanning for NO HV: Errors found!\n")
							unstable("There were errors in the log scanning. Please check the logs")
						} else {
							checkStatuses = checkStatuses.concat("1. Log scanning for NO HV: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }


        stage('Checking Logs for VM capacity issues (No VM in Pools)') {
            when {
                expression {
                    checkPools == true
                }
            }
            steps {
                script {
					try {
						CHECK_IN_ELK2 = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME2", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK2
						if (CHECK_IN_ELK2.toString().contains("No suitable nodes are found")) {
							checkInElkNoVM = true
							checkStatuses = checkStatuses.concat("2. Log scanning for NO VM: Errors found!\n")
							unstable("There were errors in the log scanning. Please check the logs")
						} else {
							checkStatuses = checkStatuses.concat("2. Log scanning for NO VM: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }

        stage('Checking Logs for VM  create failed') {
            when {
                expression {
                    checkPools == true
                }
            }
            steps {
                script {
					try {
						CHECK_IN_ELK3 = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME3", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK3
						if (CHECK_IN_ELK3.toString().contains("ERROR")) {
							checkInElkVMCreateFailed = true
							checkStatuses = checkStatuses.concat("3. Log scanning for VM create failed: Errors found!\n")
							unstable("There were errors in the log scanning. Please check the logs")
						} else {
							checkStatuses = checkStatuses.concat("3. Log scanning for VM create failed: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }

        stage('Checking Logs for VM  create timeout') {
           when {
                expression {
                    checkPools == true
                }
            }
            steps {
                script {
					try {
						CHECK_IN_ELK4 = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME4", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK4
						if (CHECK_IN_ELK4.toString().contains("ERROR")) {
							checkInElkVMCreateTimeout = true
							checkStatuses = checkStatuses.concat("4. Log scanning for VM create Timeout: Errors found!\n")
							unstable("There were errors in the log scanning. Please check the logs")
						} else {
							checkStatuses = checkStatuses.concat("4. Log scanning for VM create Timeout: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }

        stage('Checking Logs for repo:50000 issues') {
           when {
                expression {
                    checkPools == true
                }
            }
            steps {
                script {
					try {
						CHECK_IN_ELK5 = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME5", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK5
						if (CHECK_IN_ELK5.toString().contains("ERROR")) {
							checkInElkRepoFailed = true
							checkStatuses = checkStatuses.concat("5. Log scanning for Repo 50000 issues: Errors found!\n")
							unstable("There were errors in the log scanning. Please check the logs")
						} else {
							checkStatuses = checkStatuses.concat("5. Log scanning for Repo 50000 issues: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }

       stage('Checking Logs GMP connection issues') {
           when {
                expression {
                    checkPools == true
                }
            }
            steps {
                script {
					try {
						CHECK_IN_ELK8 = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME8", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK8
						if (CHECK_IN_ELK8.toString().contains("Caused")) {
							checkInElkGMPConnectionIssues = true
							checkStatuses = checkStatuses.concat("6. Log scanning for GMP connection issues: Errors found!\n")
							unstable("There were errors in the log scanning for GMP connection issues. It could be due to network issues or GMP is overloaded, but GMP operations will suffer.")
						} else {
							checkStatuses = checkStatuses.concat("6. Log scanning for GMP connection issues: OK!\n")
						}
					}
					catch (err) {
						echo err.getMessage()
						unstable("The stage Failed, but it should continue anyway!")
					}
                }
            }
        }

        stage('Statistics for Orchestrator Pool Size maintainer') {

           when {
                expression {
                    checkPools == true
                }
            }
            steps {
                script {
					try {
						CHECK_IN_ELK6 = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME6", returnStdout: true).toString().trim()
						CHECK_IN_ELK6_HITS = sh(script: "cd $JOBS_PATH && java -jar $JOBS_PATH/KibanaSearch.jar -l $DC -k $TARGET -u $AUTH_USR -p $AUTH_PSW -c $CHECK_NAME6 -d", returnStdout: true).toString().trim()
						echo CHECK_IN_ELK6
						checkStatuses = checkStatuses.concat("7. Statistics for Orchestrator Pool Size maintainer: " + CHECK_IN_ELK6_HITS + "\n")
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
                    checkPools == true && (checkInElkNoHV == true || checkInElkNoVM == true || checkInElkVMCreateTimeout == true || checkInElkVMCreateFailed == true || checkInElkRepoFailed == true || checkInElkGMPConnectionIssues == true)
                }
            }
            steps {
                script {
                    def bodymail = """

1. Metric check from CockpitAPI> CloudCotrolers to see if there are CRITICAL pools.
2. Log check for No suitable HV in GMP to start a new VM.
"No suitable hypervisors found" AND component: orchestrator AND file_name: aws
3. Log check for No suitable VM to start an application.
"java.lang.Exception: No suitable nodes are found" AND component: orchestrator AND file_name: ljs_trace
4. Log check for VM create failed due to errors in GMP.
"CreateVmTaskExecutor trigger create VM failed for task" AND component: orchestrator
5. Log check for VM create Timeout due to GMP slowness or other issues.
"after create request to IaaS was not started in give timeout period" AND component: orchestrator
6. Log check for Repo 50000 issues. This will glow yellow if Repo is slow or dead.
"Can't install packages to"  AND "Can't start HttpsTunnel to repository" AND file_name:ljs_trace
7. Log check for GMP connection issues. It might be due to network or overloaded GMP. Connection resets and timeouts will appear in the logs, also if Orchestrator is unable to see details for resource pool.
"Cannot describe availability zone" AND component: orchestrator AND file_name: aws
8. Statistics for Orchestrator Pool size maintainer. Look Here if you want to see if Orchestrator is trying to create new VM's in GMP. It returns the number of hits. FYDI check the logs.
"PoolSizeMaintainer" AND !"no need to do anything" AND component: orchestrator AND file_name: ljs_trace

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
                                subject: "IGOR: ${WHERE} GMP issues on  detected at: ${formattedDate} UTC Build: $BUILD_NUMBER",
                                body: "Hi Colleagues,\n\nHere is an automated report from Igor pipeline that caught some issues with GMP: \n\n"+ checkStatuses + bodymail + "\n"  + "\nRegards, \nIgor"
                        echo "Mail Sent to " + toAddress

                    }
                }

            }

        }
    }
