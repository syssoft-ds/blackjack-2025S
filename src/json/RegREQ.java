package json;

import utilities.enums.Role;

public class RegREQ implements BlackJackMessage{

    public String type = "register";
    public Role role;
    public String name;
    public int credit;

    public RegREQ(){}

    public RegREQ(Role role, int credit, String name){
        this.role = role;
        this.name = name;
        this.credit = credit;
    }

    @Override
    public String getType(){
        return type;
    }

}
