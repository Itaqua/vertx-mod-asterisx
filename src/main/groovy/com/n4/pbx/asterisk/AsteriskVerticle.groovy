package com.n4.pbx.asterisk

import com.n4.pbx.asterisk.message.Action
import com.n4.pbx.asterisk.message.Message
import org.vertx.groovy.platform.Verticle

/*
 * Verticle that connect to an Administrator Manager Interface (AMI) of an Asterisk Server
 *
 * @author <a href="http://n4utilus.com.mx">Octavio Luna</a>
 */
class AsteriskVerticle extends Verticle{
    private eb
    private logger
    private config
    private connected=false

    String address
    String host    
    int    port    
    String username
    String secret
    String eventsToDebug = [] //['Hangup', 'Newchannel'] //['*']

    def callbacks = [:]
    def responses = [:]
    StringBuffer receivedData = new StringBuffer()
    def ami
    def socket
    String eventsAddress

    def start(){
        callbacks.clear()
        responses.clear()
        eb = vertx.eventBus
        logger = container.logger
        config = container.config
        loadParams()
        eb.registerHandler(address, eventHandler)

        ami = vertx.createNetClient()
        ami.reconnectAttempts = -1   //default 0:don't reconnect; -1: try forever
        //ami.reconnectInterval = 500 //default 1000

    }

    def stop(){
        doClose()
        eb.unregisterHandler(address, eventHandler)
    }

    /**
     * Load configuration Params
     */
    private loadParams(){
        address  = getOptionalStringConfig("address", "com.n4.asterisk")
        host     = getMandatoryStringConfig("host")
        port     = getOptionalIntConfig("port", 5038)
        username = getMandatoryStringConfig("username")
        secret   = getMandatoryStringConfig("secret")

        eventsAddress = address + ".event"
    }

    protected getMandatoryStringConfig(String fieldName) {
        def s = config[fieldName]
        if (!s) {
            throw new IllegalArgumentException(fieldName + " must be specified in config for module")
        }
        s
    }

    protected getOptionalStringConfig(String fieldName, String defaultValue) {
        config[fieldName] ?: defaultValue
    }

    protected getOptionalIntConfig(String fieldName, int defaultValue) {
        Number i = config[fieldName]
        i == null ? defaultValue : i.intValue()
    }

    private eventHandler = { message ->
        def body = message.body
        switch(body.action) {
            case "connect":
                doConnect(){ msg ->
                    message.reply(msg)
                }     
                break
            case "close":
                doClose()
                break
            default:
                logger.debug "Action: $body"
                send(new Action(body)){ msg ->
                    message.reply(msg)
                }
        }
    }

    //Establece la conexión
    private doConnect = { callback ->
        if(!connected){
            logger.debug "Opening connection"
            callbacks["doConnect"] = callback
            ami.connect(port, host, onConnect)
        }
    }

    private doClose = {
        if(connected){
            //if(reconnect) doConnect()
            send(new Action.Logoff()){rply -> logger.debug "Logged out"}
            logger.debug "Closing connection"
            socket.close()
        }
    }

    private onConnect = { asyncResult ->
        if(asyncResult.succeeded){
            
            connected = true
            logger.debug 'Socket connected'
            //Queria enviar información del Socket Conectado, pero realmente lo necesito???
            //emit(new Message.Event('event:Connect'));

            if(socket) socket.close()

            socket = asyncResult.result

            socket.dataHandler(onWelcome)

            socket.closeHandler{
                logger.debug 'Socket disconnected'
                socket = null
                connected = false
                
                if(callbacks["doConnect"]){
                    (callbacks.remove("doConnect"))(new Message.Response('response:Failed'))
                }
                emit(new Message.Event('event:Close'));
            }

            socket.endHandler{
                logger.debug "endHandler"
            }

            socket.exceptionHandler{
                logger.debug "*** exceptionHandler ***"
            }

        }else{
            logger.error "Yo no debería estar aqui.... onConnect asyncResult.unscucceed"
            asyncResult.cause().printStackTrace()
        }

    }


    private send(Action action, callback){
        logger.debug "Sending $action"
        responses[action.actionid] = "";
        if(socket){
            callbacks[action.actionid] = callback
            socket.write(action.marshall())
        }else{
            def retAct = action.clone()
            retAct.response = "Fail"
            retAct.message = "Not connected to ${host}"
            callback(retAct)
        } 
    }

    private emit(Message.Event event){
        logger.debug "eb.publish($eventsAddress, $event)"
        eb.publish(eventsAddress, event)
        logger.debug "eb.publish(${eventsAddress + event.event}, $event)"
        eb.publish(eventsAddress + event.event, event)
    }

    private emit(String eventName){
        def event = new Message.Event()
        event.event = eventName
        emit(event)
    }

    private onRawMessage(String buffer){
        logger.debug "in onRawMessage $buffer"

        if(buffer.startsWith("Event")){
            def event = new Message.Event(buffer)
            onRawEvent(event)
        }else if(buffer.startsWith("Response")){
            def response = new Message.Response(buffer)
            onRawResponse(response)
        }else logger.warn "ERROR Discarded: |$buffer|"
    }

    private onRawEvent(event){
        if(eventsToDebug.contains('*') || eventsToDebug.contains(event.event)){
            logger.debug "Got Event: $event"
        }

        if (event.actionid && responses[event.actionid] &&
                callbacks[event.actionid]) {
            if(event.event.contains('Complete') ||
                    (event.eventlist && event.eventlist.contains('Complete')) ||
                    event.event.contains('DBGetResponse')) {
                (callbacks.remove(event.actionid))(responses.remove(event.actionid))
            }else{
                responses[event.actionid].events << event
            }
        } else emit(event)

    }

    private onRawResponse(response){
        logger.debug "got Response: $response"
        if (response.message && response.message.contains('follow'))
            responses[response.actionid] = response
        else if(callbacks[response.actionid]) {
            (callbacks.remove(response.actionid))(response)
            responses.remove(response.actionid)
        }
    }

    //Esperamos por el Saludo del Asterisk!
    private onWelcome = {data ->
        logger.debug "in onWelcome"
        String welcomeData = data.toString()
        logger.debug "Welcome Message: $welcomeData"
        if(welcomeData.startsWith(Message.WELCOME_MESSAGE)){
            def eolIndex = welcomeData.indexOf(Message.EOL) + Message.EOL.length()
            receivedData << welcomeData.substring(eolIndex)
            logger.debug "set Data Handler"
            socket.dataHandler(onData)
            send(new Action.Login(username, secret)){ response ->
                if(callbacks["doConnect"])(callbacks.remove("doConnect"))(response)
                if(response.response == "Success") emit("Connected")
                else emit("LoginFailed")
            }
        }else{
            logger.fatal "No Welcome???"
            socket.close()
        }
    }

    //Esperamos por datos
    private onData = {data ->
            //logger.debug "onData: $data"
            receivedData.append(data.toString())
            def eomIndex = -1
            while((eomIndex = receivedData.indexOf(Message.EOM)) >= 0){
                String msg = receivedData.substring(0, eomIndex)
                onRawMessage(msg)
                receivedData.delete(0, eomIndex + Message.EOM.length())
            }
    }

}
