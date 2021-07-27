package com.github.moritzhst.blitzi.model;

public class SanctionDTO {
    private String money = "0€";
    private Integer points = 0;
    private Integer drivingBan = 0;

    public SanctionDTO(){
        reset();
    }

    public String getMoney() {
        return money;
    }

    public void setMoney(String money) {
        this.money = money;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
    }

    public Integer getDrivingBan() {
        return drivingBan;
    }

    public void setDrivingBan(Integer drivingBan) {
        this.drivingBan = drivingBan;
    }

    public void reset(){
        money = "0€";
        points = 0;
        drivingBan = 0;
    }
}
