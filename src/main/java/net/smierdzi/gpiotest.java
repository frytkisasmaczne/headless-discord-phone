package net.smierdzi;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

public class gpiotest {
    public static void main(String[] args) {
        final GpioController gpio = GpioFactory.getInstance();
        final GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_22);

        final GpioPinDigitalInput input04 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04);
        input04.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
        input04.addListener((GpioPinListenerDigital) event -> {
            System.out.print(event.getState().toString());
            if (event.getState().isHigh()) {
                System.exit(0);
            }
        });
        try {
            Thread.sleep(1000_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
