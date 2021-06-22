package net.smierdzi;

/*
 * Copyright 2015-2020 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import javax.sound.sampled.*;
import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class dbb extends ListenerAdapter
{
    public static final String fourdotvcid = "752987166337925174";
    public static final String fourdottextchannelid = "752987166337925173";

    public static final String nggyu = "396307639669358598";
    public static final String legmun = "691325469257367614";

    static EchoHandler handler;
    static JDA jda;
    static AudioManager audioManager;

    public static VoiceChannel fourdotvc;
    public static TextChannel fourdottextchannel;

    public static boolean connected = false;
    public static boolean ringing = false;

    public static final GpioController gpio = GpioFactory.getInstance();

    //button for disconnecting, the one usually under the earpiece, i made the logic in setup_input_gpio/input00.setlistener for that
    public static final GpioPinDigitalInput input00 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_00);

    public static final GpioPinDigitalOutput input01 = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_01);//buzzer for signaling incoming connnection
    public static final GpioPinDigitalInput input02 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02);//for calling user nggyu
    public static final GpioPinDigitalInput input03 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_03);//for calling user legmun

    public static void main(String[] args) throws LoginException, InterruptedException {
        if (args.length == 0)
        {
            System.err.println("Unable to start without token!");
            System.exit(1);
        }
        String token = args[0];

        // We only need 2 gateway intents enabled for this example:
        EnumSet<GatewayIntent> intents = EnumSet.of(
                // We need messages in guilds to accept commands from users
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.DIRECT_MESSAGES,
                // We need voice states to connect to the voice channel
                GatewayIntent.GUILD_VOICE_STATES
        );

        // Start the JDA session with default mode (voice member cache)
        jda = JDABuilder.createDefault(token, intents)         // Use provided token from command line arguments
                .addEventListeners(new dbb())  // Start listening with this listener
                .setActivity(Activity.listening("nikogo")) // Inform users that we are jammin' it out
                .setStatus(OnlineStatus.ONLINE)     // Please don't disturb us while we're jammin'
                .enableCache(CacheFlag.VOICE_STATE)         // Enable the VOICE_STATE cache to find a user's connected voice channel
                .build();                                   // Login with these options
        jda.awaitReady();
        fourdotvc = jda.getVoiceChannelById(fourdotvcid);
        fourdottextchannel = jda.getTextChannelById(fourdottextchannelid);

        setup_input_gpio();
        System.out.println("ready");
    }

    public static void setup_input_gpio() {
        input00.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh() && connected) {
                connected = false;
                disconnectvc();
            } else if(event.getState().isLow() && ringing){
                ring(false);
                connectvc();
            }
        });
        input02.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh() && !connected){
                connected = true;
                callPerson(nggyu);
            }
        });
        input03.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh() && !connected){
                connected = true;
                callPerson(legmun);
            }
        });
    }

    public static void ring(boolean rink){
        if(rink){
            input01.setState(PinState.HIGH);
        }else{
            input01.setState(PinState.LOW);
        }
        ringing = rink;
    }

    public static void callPerson(String userID){
        connectvc();
//        sendPM(userID, "halo");
        String name = "halo";
        try{
            name += jda.getUserById(userID).getName();
        }catch(NullPointerException ignored){}

        announce(name);
    }

    public static void connectvc(){
        connectTo(fourdotvc);
        jda.getPresence().setActivity(Activity.listening(audioManager.getConnectedChannel().getMembers().get(0).getUser().getName()));
    }

    public static void disconnectvc(){
        audioManager.closeAudioConnection();
        handler.close();
        handler = null;
        jda.getPresence().setActivity(Activity.listening("nikogo"));
    }

    @Override
    public void onPrivateMessageReceived(PrivateMessageReceivedEvent event){
        if(event.getMessage().getContentRaw().toLowerCase().contains("halo")){
            ring(true);
        }
    }

    public static void sendPM(String userID, String message){
        if(jda.getUserById(userID).hasPrivateChannel()){
            jda.getPrivateChannelById(userID).sendMessage(message).queue();
        }else{
            jda.openPrivateChannelById(userID)
                .flatMap(channel -> channel.sendMessage(message))
                .queue();
        }
    }

    public static void announce(String message){
        if (fourdottextchannel.sendMessage(message).isEmpty()){
            System.out.println("ERROR send msg:"+message);
        }
    }

    @Override
    public void onGuildVoiceLeave(GuildVoiceLeaveEvent event){
        if(event.getChannelLeft().getId().equals(fourdotvcid) && ringing){
            ring(false);
        }
    }




    /**
     * Connect to requested channel and start echo handler
     *
     * @param channel
     *        The channel to connect to
     */
    private static void connectTo(VoiceChannel channel)
    {
        Guild guild = channel.getGuild();
        // Get an audio manager for this guild, this will be created upon first use for each guild
        audioManager = guild.getAudioManager();
        // Create our Send/Receive handler for the audio connection
        handler = new EchoHandler();
        System.out.println("echohandler somehow created");

        // The order of the following instructions does not matter!

        // Set the sending handler to our echo system
        audioManager.setSendingHandler(handler);
        // Set the receiving handler to the same echo system, otherwise we can't echo anything
        audioManager.setReceivingHandler(handler);
        // Connect to the voice channel
        audioManager.openAudioConnection(channel);
    }

    public static class EchoHandler implements AudioSendHandler, AudioReceiveHandler
    {
        /*
            All methods in this class are called by JDA threads when resources are available/ready for processing.
            The receiver will be provided with the latest 20ms of PCM stereo audio
            Note you can receive even while setting yourself to deafened!
            The sender will provide 20ms of PCM stereo audio (mic) once requested by JDA
            When audio is provided JDA will automatically set the bot to speaking!
         */
        private final Queue<byte[]> queue = new ConcurrentLinkedQueue<>();
        final AudioFormat receivedAudioFormat = AudioReceiveHandler.OUTPUT_FORMAT;
        final AudioFormat sentAudioFormat = AudioSendHandler.INPUT_FORMAT;
        SourceDataLine sourceDataLine = null;
        TargetDataLine targetDataLine = null;
        private final int packetSize = 3840;
        byte[] tempbuffer = new byte[packetSize];


        public EchoHandler(){
            try{
                sourceDataLine = AudioSystem.getSourceDataLine(receivedAudioFormat);
                sourceDataLine.open();
                System.out.println(sourceDataLine.getFormat());
                sourceDataLine.start();
                System.out.println(receivedAudioFormat);

                System.out.println("and now for mic");

                targetDataLine = AudioSystem.getTargetDataLine(sentAudioFormat);
                targetDataLine.open(sentAudioFormat, packetSize);
                System.out.println(targetDataLine.getFormat());
                targetDataLine.start();
                System.out.println("intialized audio devices?");
                targetDataLine.read(tempbuffer, 0, tempbuffer.length);
                System.out.println(sentAudioFormat);



            }
            catch(LineUnavailableException lue){
                System.out.println("shit waded, wrong sourcedataline info or whatever");
                lue.printStackTrace();
            }
            System.out.println("lets goooo");
            if(sourceDataLine != null && targetDataLine != null){
                System.out.println(sourceDataLine.getLineInfo());
                System.out.println(targetDataLine.getLineInfo());
            } else {
                System.out.println("well shit");
            }

        }

        /* Receive Handling */

        @Override // combine multiple user audio-streams into a single one
        public boolean canReceiveCombined()
        {
            return true;//i pass anything i get into the audiosystem and let it sort it out with its own queues
        }

        @Override
        public void handleCombinedAudio(CombinedAudio combinedAudio)
        {
            // we only want to send data when a user actually sent something, otherwise we would just send silence
//            if (combinedAudio.getUsers().isEmpty())
//                return;
            // this is probably TODO, not sure

            byte[] data = combinedAudio.getAudioData(1f); // volume at 100% = 1.0 (50% = 0.5 / 55% = 0.55)
            sourceDataLine.write(data, 0, data.length);
        }

        /* send handling */

        @Override
        public boolean canProvide()
        {
            return true;//mic has continuous data so worst case it just waits a while i think
        }

        @Override
        public ByteBuffer provide20MsAudio()
        {
            targetDataLine.read(tempbuffer, 0, packetSize);//read 20ms audio to buffer
            return tempbuffer == null ? null : ByteBuffer.wrap(tempbuffer);//wrap and pass the buffer
        }

        @Override
        public boolean isOpus()
        {
            // since we send audio that is received from discord we don't have opus but PCM
            return false;
        }

        public void close(){
            sourceDataLine.stop();
            sourceDataLine.flush();
            sourceDataLine.close();
            targetDataLine.stop();
            targetDataLine.flush();
            targetDataLine.close();
        }

    }
}