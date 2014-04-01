package com.n4.pbx.asterisk.message

import org.codehaus.groovy.ast.Variable

/**
 * Created by octavio on 12/03/14.
 */
class Message extends HashMap{
    static final String EOL = "\r\n"
    static final String EOM = EOL + EOL
    static final String WELCOME_MESSAGE = "Asterisk Call Manager"

    def variables = [:]

    Message(){}
    Message(String buffer){
        unmarshall(buffer)
    }

    def marshall(){
        def buffer = new StringWriter()

        this.each{key, value ->
            buffer << key << ': ' << value << EOL
        }

        variables.each {key, val ->
            buffer << 'Variable: ' << key << '=' << val << EOL
        }

        (buffer << EOL).toString()
    }

    String toString(){
        return marshall()
    }

    def unmarshall(String data){
        def lines = data.split(EOL)
        try{
        lines.each{ line ->
            def parts = line.split(":")
            def key = "value"
            if(parts.size()>1){  //&& parts.contains("--END COMMAND--")){
                key = parts.first()
                parts = parts.tail()
            }   

            def value = (parts.size() > 1)?parts.join(":"):(parts)?parts[0]:''
            def keySafe = key.replace("-", "_").toLowerCase()
            def valueSafe = value.replaceAll(/^\s+/, '')
            if(keySafe == 'variable' && valueSafe.contains('=')){
                def variable = valueSafe.split('=')
                variables[variable[0]]=variable[1]

            }else this[keySafe] = valueSafe

        }
        }catch(Exception e){
            println "ERROR: $lines"
            e.printStackTrace()
            System.exit(0)
        }
    }

    static class Response extends Message{
        Response(String data){
            super(data)
            this.events = []
        }
    }

    static class Event extends Message{
        Event(){}
        Event(String data){
            super(data)
        }
    }
}