package com.github.moritzhst.blitzi.model;


import android.databinding.Observable;
import android.databinding.ObservableBoolean;
import android.databinding.ObservableChar;
import android.databinding.ObservableDouble;
import android.databinding.ObservableField;
import android.databinding.ObservableLong;

public class AppDTO {
    public ObservableDouble currentSpeed = new ObservableDouble();
    public ObservableLong allowedSpeed = new ObservableLong();
    public ObservableLong potentialPoints = new ObservableLong();
    public ObservableLong potentialPenalty = new ObservableLong();
    public ObservableBoolean outside = new ObservableBoolean();
}
