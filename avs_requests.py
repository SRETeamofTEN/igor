#!/usr/bin/python
import sys
import requests
import time
import logging

from requests.adapters import HTTPAdapter


def check_direct_url():
    if avs_status == "UP":
        print("Checking directly to destination URL...")

        for i in range(1, 4):
            response2 = requests.get("%s" % direct_url,  auth=(USERNAME, PASSWORD), timeout=30)
            up_down = str(response2.status_code)
            text = str(" Direct check Response code: ")
            print(str(response2.url) + text + up_down)
            print("Response content is: " + response2.content)
            time.sleep(2)

logging.basicConfig()
logging.getLogger().setLevel(logging.DEBUG)
requests_log = logging.getLogger("requests.packages.urllib3")
requests_log.setLevel(logging.DEBUG)
requests_log.propagate = True

mon_id = sys.argv[1]
direct_url = sys.argv[2]
USERNAME = sys.argv[3]
PASSWORD = sys.argv[4]
TOKEN = open('/usr/sap/ljs/home/.jenkins/jobs/avs_token.txt', 'r').read()
bearer = 'Bearer ' + TOKEN


try:
        response2 = requests.get(
        "https://avs-backend.cfapps.us10.hana.ondemand.com/api/v2/evaluationdata/%s/status" % mon_id,
            headers={'Accept':'application/json', 'Authorization': bearer},
            timeout=30,
        )

        if response2.status_code != 200:
            raise requests.ConnectionError("Expected status code 200, but got {}".format(response2.status_code))

        print("Checking monitor status in Availability Service...")
        res_json = response2.json()
        dictOfWords = dict(res_json[0])
        print("Monitor name: " + dictOfWords['evaluationName'])
        print("AVS STATUS: " + dictOfWords['status_value'])
        print( dictOfWords['outage_reason'])
        print("\n")
        avs_status =(dictOfWords['status_value'])
        check_direct_url()


except NameError:
    pass
except requests.ConnectTimeout:
    print( "ERROR: Connection timeout. Could not fetch the information for the requested timeout. US10 might be DOWN or"
          " there is no connection to it.")

except requests.ConnectionError:
    print( "ERROR: Connection Error ocured. Check if the outbound connection is possible or if CF US10 is not DOWN ")

except requests.ReadTimeout:
    print( "ERROR: Read timeout. The destination did not replied back in 60 seconds. It might be slow or DOWN ")

except requests.exceptions.HTTPError as err:
    raise SystemExit(err)
except Exception as x:
    print( "ERROR: Failed to get complete the task and the exception is not handled. Please check the type of error in the traceback", x.__class__.__name__)

