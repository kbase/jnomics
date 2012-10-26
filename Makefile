TARGET ?= /kb/deployment

SERVICE=jnomics
SERVICE_DIR=$(TARGET)/services/$(SERVICE)
DEPLOYMENT_DIR=$(TARGET)
CLIENT_BIN_DIR=$(DEPLOYMENT_DIR)/bin
CLIENT_LIB_DIR=$(DEPLOYMENT_DIR)/lib
CLIENT_CONF_DIR=$(DEPLOYMENT_DIR)/conf
CLIENT_DOCS_DIR=$(DEPLOYMENT_DIR)/docs
CLIENT_CERT_DIR=$(DEPLOYMENT_DIR)/cert
SERVICE_BIN_DIR=$(SERVICE_DIR)/bin
SERVICE_CONF_DIR=$(SERVICE_DIR)/conf
SERVICE_LIB_DIR=$(SERVICE_DIR)/lib


JAVA_HOME:=/kb/runtime/java
ANT_HOME:=/kb/runtime/ant
THRIFT_HOME:=/kb/runtime/thrift
PATH:=${JAVA_HOME}/bin:${ANT_HOME}/bin:${THRIFT_HOME}/bin:${PATH}

all:

deploy: deploy-jnomics

test:
	cd kbase-test && ./test_var_service.sh

make-dest-dir:
	mkdir -p $(SERVICE_BIN_DIR)
	mkdir -p $(SERVICE_CONF_DIR)
	mkdir -p $(SERVICE_LIB_DIR)
	mkdir -p $(CLIENT_BIN_DIR)
	mkdir -p $(CLIENT_LIB_DIR)
	mkdir -p $(CLIENT_CONF_DIR)
	mkdir -p $(CLIENT_DOCS_DIR)
	mkdir -p $(CLIENT_CERT_DIR)

build-jnomics:
	ant jar

deploy-jnomics: make-dest-dir build-jnomics
	cp bin/jnomics.jar $(SERVICE_LIB_DIR)
	cp bin/jnomics.jar $(CLIENT_LIB_DIR)
	cp conf/jnomics-kbase-client.properties $(CLIENT_CONF_DIR)
	cp conf/jnomics-kbase-server.properties $(SERVICE_CONF_DIR)
	cp bin/jkbase $(CLIENT_BIN_DIR)
	cp bin/start-service.sh $(SERVICE_BIN_DIR)
	cp bin/start-data-server.sh $(SERVICE_BIN_DIR)
	cp bin/start-compute-server.sh $(SERVICE_BIN_DIR)
	cp docs/KBASE-DEPLOY-README $(CLIENT_DOCS_DIR)/JNOMICS-CLIENT-README
	cp cert/truststore.jks $(CLIENT_CERT_DIR)
