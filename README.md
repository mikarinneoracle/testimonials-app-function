# Testimonials OCI Functions App

## Architecture 

![architecture](testimonials_arch_2.png)

The Authorizer function has two purposes: To handle sign in on both enviroments (localhost and OCI) and implement auth/security in APIGW as an authorizer function.
<p>
The App function has three purposes: To show the Welcome -page with a carousel, to sign-up and to add a testimonial either via POST or JSON REST API.
<p>
(Functions could be divided into more granural functions but just using these two for simplicity.)

## Authorizer function repo
https://github.com/mikarinneoracle/testimonials-authorizer-function

## UI and DB schema repo
https://github.com/mikarinneoracle/testimonials-ui-and-schema

To create database with schema and data:

<pre>
./sql /nolog
set cloudconfig Wallet.zip
conn admin/<password>@testimonials_tp
lb update -changelog-file control-file.xml
</pre>
For UI copy the files into Object Storage bucket.
<br>
For the files <code>welcome.html</code> and <code>welcome_local.html</code> create PARs to access outside from your tenancy.

## Terraform Stack for OCI DevOps
https://github.com/mikarinneoracle/testimonials-devops-tf-stack

Terraform stack will create all resources and configs needed to run the Testimonials app excluding
database, OCI Vault and policies.
<p>
It will also create OCI DevOps project for the Testimonials app CI/CD to OCI with GraalVM native builds for Java.
<p>

## Local Fn config

<pre>
Fn contexts:
fn list context   

CURRENT NAME    PROVIDER        API URL                                                 REGISTRY
*       default default         http://localhost:8080                                   
        oci     oracle          https://functions.eu-frankfurt-1.oraclecloud.com        fra.ocir.io/frs...f35/

fn use context default
fn create app demo

Database (e.g. Autonomous):

fn config app demo DB_USER admin

TLS:
fn config app demo DB_URL (description= (retry_count=20)(retry_delay=3)(address=(protocol=tcps)(port=1521)(host=adb.eu-frankfurt-1.oraclecloud.com))(connect_data=(service_name=g9......_fntest_tp.adb.oraclecloud.com))(security=(ssl_server_dn_match=yes)))
fn config app demo DB_PASSWORD WelcomeFolks123##
fn config app demo DB_WALLET_PASSWORD WelcomeFolks123##

mTLS with Vault:
fn config app demo DB_URL fntest_tp
fn config app demo DB_PASSWORD 'ocid1.vaultsecret.oc1.eu-frankfurt-1.amaaaa....izsxrjyyrxq'
fn config app demo DB_WALLET_PASSWORD 'ocid1.vaultsecret.oc1.eu-frankfurt-1.amaaaa....2gizsxrjyyrxq'
fn config app demo DB_WALLET_OCID 'ocid1.autonomousdatabase.oc1.eu-frankfurt-1.anthel....ihop3ziueesgq'

Triggers:
fn config app demo APP_URL http://localhost:8080/t/demo/invoke
fn config app demo WELCOME_URL http://localhost:8080/t/demo/invoke
fn config app demo AUTH_URL http://localhost:8080/t/demo/authenticate
fn config app demo SIGNUP_URL http://localhost:8080/t/demo/invoke?action=signup

IDCS:
fn config app demo CLIENT_ID 257b2496....ed90535a
fn config app demo CLIENT_SECRET idcscs-4f8....31c32932
fn config app demo IDCS_URL idcs-0a68e6....05a7b30
fn config app demo PROFILE_ID 08e1....9d65f44

GenAI:
fn config app demo COMPARTMENT_OCID 'ocid1.compartment.oc1..aaaaaa....nhmvgiqdatqgq'
fn config app demo GENAI_OCID 'ocid1.generativeaimodel.oc1.eu-frankfurt-1.amaaaa....gdcdhdu2whq'
fn config app demo GENAI_ENDPOINT https://inference.generativeai.eu-frankfurt-1.oci.oraclecloud.com

UI:
fn config app demo PAGE_URL https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/frs...f35/b/pub/o/testimonial.html
fn config app demo SIGNUP_PAGE_URL https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/frs...f35/b/pub/o/login.html
fn config app demo CAROUSEL_PAGE_URL https://objectstorage.eu-frankfurt-1.oraclecloud.com/n/frs...f35/b/pub/o/login_carousel.html

fn list config app demo

Deploy to localhost:
fn deploy --app demo  --local

Triggers:
fn list triggers demo 
FUNCTION                NAME            ID                              TYPE    SOURCE          ENDPOINT
authorizerfnjava        authenticate    01K1WEBE30NG8G00GZJ0000008      http    /authenticate   http://localhost:8080/t/demo/authenticate
fnsimplejava            invoke          01K1WE6H9YNG8G00GZJ0000003      http    /invoke         http://localhost:8080/t/demo/invoke

</pre>

## OCI

For OCI use the following API Gateway URLs (instead of triggers) in Application config:

<pre>
APP_URL https://dc7ll...yzb4q.apigateway.eu-frankfurt-1.oci.customer-oci.com/testimonial
WELCOME_URL https://dc7ll...yzb4q.apigateway.eu-frankfurt-1.oci.customer-oci.com/welcome
AUTH_URL https://dc7ll...yzb4q.apigateway.eu-frankfurt-1.oci.customer-oci.com/login
SIGNUP_URL https://dc7ll...yzb4q.apigateway.eu-frankfurt-1.oci.customer-oci.com/welcome?action=signup
</pre>

Deploy to OCI:
<pre>
fn use context OCI
fn deploy --app demo-arm
</pre>

## Run the App function <code>main</code> for mTLS testing

Set env vars:
<pre>
export DB_WALLET_OCID="ocid1.autonomousdatabase.oc1.eu-frankfurt-1.anthel...ueesgq"
export DB_URL=fntest_tp
export DB_USER=admin
export DB_WALLET_PASSWORD="ocid1.vaultsecret.oc1.eu-frankfurt-1.amaaaa....rjyyrxq"
export DB_PASSWORD="ocid1.vaultsecret.oc1.eu-frankfurt-1.amaaaa....rjyyrxq"

mvn clean package
java -jar target/Hellofunc-1.0-SNAPSHOT-jar-with-dependencies.jar
</pre>