package net.smierdzi;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.util.EnumSet;

public class gpiotest {

    public static final GpioController gpio = GpioFactory.getInstance();
    public static final GpioPinDigitalInput input00 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_00);
    public static final GpioPinDigitalOutput input01 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01);
    public static final GpioPinDigitalInput input02 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02);
    public static final GpioPinDigitalInput input03 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_03);
    public static void main(String[] args) {


        setup_input_gpio();
        System.out.println("waiting");
        while (true){
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }

    public static void setup_input_gpio() {
        input00.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh()) {
                System.out.println("0 high");
            } else if(event.getState().isLow()){
                System.out.println("0 low");
            }
        });
        input02.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh()) {
                System.out.println("2 high");
            } else if(event.getState().isLow()){
                System.out.println("2 low");
            }
        });
        input03.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh()) {
                System.out.println("3 high");
                input01.setState(PinState.HIGH);
            } else if(event.getState().isLow()){
                System.out.println("3 low");
                input01.setState(PinState.LOW);
            }
        });
    }

}
