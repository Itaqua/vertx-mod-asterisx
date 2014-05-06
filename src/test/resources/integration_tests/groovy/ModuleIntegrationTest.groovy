/*
 * Example Groovy integration test that deploys the module that this project builds.
 *
 * Quite often in integration tests you want to deploy the same module for all tests and you don't want tests
 * to start before the module has been deployed.
 *
 * This test demonstrates how to do that.
 */

import static org.vertx.testtools.VertxAssert.*

// And import static the VertxTests script
import org.vertx.groovy.testtools.VertxTests;

asterisk_local = "asterisk-local"

// The test methods must being with "test"
def testLogin() {
  container.logger.info("in testLogin()")

  vertx.eventBus.registerHandler(asterisk_local + ".eventConnected"){ message ->
    assertNotNull message
    def body = message.body
    assertNotNull body
    println body

    assert body.event == "Connected"
    testComplete()
  }
  vertx.eventBus.send(asterisk_local, [action:"connect"])
}

def testLoginP2P() {
  container.logger.info("in testLoginP2P()")

  vertx.eventBus.sendWithTimeout(asterisk_local, [action:"connect"], 2000){ asyncResult ->
    assertNotNull asyncResult
    assertTrue asyncResult.succeeded
    def body = asyncResult.result.body
    assertNotNull body
    println body

    assert body.response == "Success"
    testComplete()
  }
}

def testActionCoreShowChannels() {
    container.logger.info("in testCoreShowChannels()")

    vertx.eventBus.registerHandler(asterisk_local + ".event"){ message ->
        if(message.body.event == "Connected"){
            vertx.eventBus.send(asterisk_local, [action:'CoreShowChannels']){ reply ->
                assertNotNull reply
                println reply.body
                testComplete()
            }
        }
    }
    vertx.eventBus.send(asterisk_local, [action:"connect"])
}

def testActionCommandSipShowUsers() {
    container.logger.info("in testActionCommandSipShowUsers()")

    vertx.eventBus.registerHandler(asterisk_local + ".event"){ message ->
        if(message.body.event == "Connected"){
            vertx.eventBus.send(asterisk_local, [action:'command', command:'sip show users']){ reply ->
                assertNotNull reply
                println reply.body
                assertTrue reply.body.value.contains("--END COMMAND--")
                testComplete()
            }
        }
    }
    vertx.eventBus.send(asterisk_local, [action:"connect"])
}

def testActionOriginate() {
    container.logger.info("in testActionOriginate()")

    def msg = [action:'originate',channel:'SIP/103',context:'normal',exten:'101',priority:'1',callerid:'3125551212']
    vertx.eventBus.send(asterisk_local, msg){
        println "Ret Val"
        testComplete()
    }
}

// Make sure you initialize
VertxTests.initialize(this)

// The script is execute for each test, so this will deploy the module for each one
// Deploy the module - the System property `vertx.modulename` will contain the name of the module so you
// don't have to hardecode it in your tests
println System.getProperty("vertx.modulename")

container.deployModule(System.getProperty("vertx.modulename"), [
    address  : asterisk_local,
    host     : "asterisk.localhost",
    username : "manager",
    secret   : "password"
]){ asyncResult ->
  // Deployment is asynchronous and this this handler will be called when it's complete (or failed)
    assertTrue asyncResult.succeeded
    if(asyncResult.succeeded){
        assertNotNull("deploymentID should not be null", asyncResult.result())

      // If deployed correctly then start the tests!
        VertxTests.startTests(this)
    }else{
        asyncResult.cause().printStackTrace()
        //fail(asyncResult.cause().getMessage())
    }
}
