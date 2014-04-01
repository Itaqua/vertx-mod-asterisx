package com.n4.pbx.asterisk.message
/**
 * Created by octavio on 13/03/14.
 */
class Action extends Message {
    static long nextId = 0
    public synchronized static nextId(){
        return "${nextId++}"
    }

    //@String action

    Action(String name){
        this([action : name])
    }

    Action(Map map){
        map.each{key, val ->
            this[key] = val
        }
        if(!this.actionid) this.actionid = nextId()
    }

    /** ACCIONES ***  Son Necesarias??? se pueden deducir de arriba D:
     Y pues como que con el Buffer, pierden sentido ***/

    /** ACCIONES ***/
    static class Login extends Action{
        //@String username
        //@String secret

        Login(String username, String password){
            super("Login")
            this.username = username
            this.secret = password
        }
    }

    static class Logoff extends Action{
        Logoff(){
            super("Logoff")
        }
    }

    static class Ping extends Action{
        Ping(){
            super("Ping")
        }
    }    

    static class CoreShowChannels extends Action{
        CoreShowChannels(){
            super("CoreShowChannels")
        }
    }

    static class Command extends Action{
        Command(command){
            super("Command")
            this.command = command
        }
    }
}
