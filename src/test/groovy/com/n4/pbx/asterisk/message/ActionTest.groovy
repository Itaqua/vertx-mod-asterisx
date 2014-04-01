package com.n4.pbx.asterisk.message

/**
 * Created by octavio on 13/03/14.
 */
class ActionTest extends GroovyTestCase{
    String EOL = "\r\n"
    String EOM = EOL + EOL
    void testLoginAction(){
        def user = [
                username : "some_username",
                password : "my_password"
        ]

        def login = new Action.Login(user.username, user.password)

        assert login.toString() == (new Message("action: Login${EOL}actionid: ${login.actionid}${EOL}username: ${user.username}${EOL}secret: ${user.password}${EOM}")).toString()
    }


}
