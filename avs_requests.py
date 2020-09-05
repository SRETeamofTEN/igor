#!/usr/bin/python
import sys
import requests
import time
import logging
from requests.packages.urllib3.util.retry import Retry
from requests.adapters import HTTPAdapter
import datetime

#ENABLE FULL DEBUGGING for HTTP requests with uncommenting below block
# try:
#     from http.client import HTTPConnection # py3
# except ImportError:
#     from httplib import HTTPConnection # py2
# HTTPConnection.debuglevel = 1

def requests_retry_session(
    retries=5, # Retry count
    backoff_factor=1,
    status_forcelist=range(500, 599), #A set of HTTP status codes that we should force a retry on. e.g all 5xx errors.
    session=None,
):
    session = session or requests.Session()
    retry = Retry(
        total=retries,
        read=retries,
        connect=retries,
        backoff_factor=backoff_factor,
        status_forcelist=status_forcelist,
        method_whitelist=["HEAD", "GET", "PUT", "DELETE", "OPTIONS", "TRACE", "POST"]
    )
    adapter = HTTPAdapter(max_retries=retry, pool_connections=100, pool_maxsize=100)
    session.mount('http://', adapter)
    session.mount('https://', adapter)
    return session

def check_direct_url():

    print("Checking directly to destination URL...")

    for i in range(1, 4):
        response2 = requests_retry_session().get("%s" % direct_url,  auth=(USERNAME, PASSWORD), timeout=30)
        up_down = str(response2.status_code)
        text = str(" Direct check Response code: ")
        print(str(response2.url) + text + up_down)
        if (CONTENT == "PRINT" or CONTENT == "print" or CONTENT == "Print"):
            print("Response content is: " + str(response2.content))
        time.sleep(2)


logging.basicConfig(
    format='%(asctime)s %(levelname)-8s %(message)s',
    level=logging.INFO,
    datefmt='%Y-%m-%d %H:%M:%S')

logging.getLogger().setLevel(logging.DEBUG)
requests_log = logging.getLogger("requests.packages.urllib3")
requests_log.setLevel(logging.DEBUG)
requests_log.propagate = True

try:
    mon_id = sys.argv[1]
    direct_url = sys.argv[2]
    USERNAME = sys.argv[3]
    PASSWORD = sys.argv[4]
    CONTENT = sys.argv[5]
    TOKEN =  "1234"
    bearer = 'Bearer ' + TOKEN
except IndexError or NameError:
    print("Usage of the application requires all of the options: \n "
          "1. AvS monitor ID to be checked \n "
          "2. Direct URL from definition tab in AvS \n "
          "3. Username to be provided \n "
          "4. Password \n "
          "5. Type PRINT/print to display response body or anything else not to display it, like noprint. \n ")
    sys.exit(1)

try:
        response2 = requests_retry_session().get(
        "https://httpbin.org/status/403", #% mon_id,
            headers={'Accept':'application/json', 'Authorization': bearer},
            timeout=3,
        )
        if (response2.status_code == 401) or (response2.status_code == 403):
            print("{} ERROR: Authentication failed! : Response: ".format(datetime.datetime.now().replace(microsecond=0)) + str(response2.status_code) + " Please check if you have added the correct credentials. ")
        if response2.status_code != 200:
            print("{} ERROR: Returned: ".format(datetime.datetime.now().replace(microsecond=0)) + str(response2.status_code) + " Will try to check directly and close the script afterwards.")
            check_direct_url()
            print("{} ERROR: Expected 200 response code, but got: ".format(datetime.datetime.now().replace(microsecond=0)) + str(response2.status_code) + " Exiting now!")
            sys.exit(1)

        else:
            print("Checking monitor status in Availability Service...")
            res_json = response2.json()
            dictOfWords = dict(res_json[0])
            print("Monitor name: " + dictOfWords['evaluationName'])
            print("AVS STATUS: " + dictOfWords['status_value'])
            print(dictOfWords['outage_reason'])
            print("\n")
            avs_status =(dictOfWords['status_value'])
            check_direct_url()



except requests.ConnectTimeout:
    print( "{} ERROR: Connection timeout. Could not fetch the information for the requested timeout. US10 might be having some issues or"
          " there is no connection to it.".format(datetime.datetime.now().replace(microsecond=0)))

except requests.exceptions.RetryError as errr:
    print("{} ERROR: Too Many retries failed.".format(datetime.datetime.now().replace(microsecond=0)), errr)
except requests.ConnectionError:
    print("{} ERROR: Connection Error. Check if the outbound connection is possible or if CF US10 is not failing. ".format(datetime.datetime.now().replace(microsecond=0)))

except requests.ReadTimeout:
    print("{} ERROR: Read timeout. The destination did not replied back in 60 seconds. It might be slow or failing.".format(datetime.datetime.now().replace(microsecond=0)))

except requests.exceptions.HTTPError as err:
    raise SystemExit(err)
except Exception as x:
    print( "{} ERROR: Failed to get complete the task and the exception is not handled. "
           "Please check the type of error in the traceback, "
           "which is: ".format(datetime.datetime.now().replace(microsecond=0)), x.__class__.__name__)
