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

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioReceiveHandler;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.audio.CombinedAudio;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
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
    public static final String fourdotguildid = "752987166337925170";

    public static final String nggyu = "396307639669358598";

    static EchoHandler handler;
    static JDA jda;
    static AudioManager audioManager;

    public static VoiceChannel fourdotvc;
    public static TextChannel fourdottextchannel;

    public static boolean connected = false;

    public static final GpioController gpio = GpioFactory.getInstance();
    public static final GpioPinDigitalInput input00 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_00);
    public static final GpioPinDigitalInput input02 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_02);
    public static final GpioPinDigitalInput input03 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_03);
    public static final GpioPinDigitalInput input04 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_04);
    public static final GpioPinDigitalInput input05 = gpio.provisionDigitalInputPin(RaspiPin.GPIO_05);

    public static void main(String[] args) throws LoginException, InterruptedException {
        if (args.length == 0)
        {
            System.err.println("Unable to start without token!");
            System.exit(1);
        }
        String token = args[0];

        input00.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh() && connected) {
                connected = false;
                disconnectvc();
            }
        });

        input02.addListener((GpioPinListenerDigital) event -> {
            if(event.getState().isHigh() && !connected){
                connected = true;
                callPerson(nggyu);
            }
        });



        // We only need 2 gateway intents enabled for this example:
        EnumSet<GatewayIntent> intents = EnumSet.of(
                // We need messages in guilds to accept commands from users
                GatewayIntent.GUILD_MESSAGES,
                // We need voice states to connect to the voice channel
                GatewayIntent.GUILD_VOICE_STATES
        );

        // Start the JDA session with default mode (voice member cache)
        jda = JDABuilder.createDefault(token, intents)         // Use provided token from command line arguments
                .addEventListeners(new dbb())  // Start listening with this listener
                .setActivity(Activity.listening("ciebie")) // Inform users that we are jammin' it out
                .setStatus(OnlineStatus.ONLINE)     // Please don't disturb us while we're jammin'
                .enableCache(CacheFlag.VOICE_STATE)         // Enable the VOICE_STATE cache to find a user's connected voice channel
                .build();                                   // Login with these options
        jda.awaitReady();
        fourdotvc = jda.getVoiceChannelById(fourdotvcid);
        fourdottextchannel = jda.getTextChannelById(fourdottextchannelid);
    }

    public static void callPerson(String userID){
        connectTo(fourdotvc);
        fourdottextchannel.sendMessage("halo <@"+userID+">").queue();
    }

    public static void disconnectvc(){
        audioManager.closeAudioConnection();
        handler.close();
        handler = null;
    }

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event)
    {
        Message message = event.getMessage();
        User author = message.getAuthor();
        String content = message.getContentRaw();
        Guild guild = event.getGuild();

        // Ignore message if bot
        if (author.isBot())
            return;

        if (content.startsWith("!echo "))
        {
            String arg = content.substring("!echo ".length());
            onEchoCommand(event, guild, arg);
        }
        else if (content.equals("!echo"))
        {
            onEchoCommand(event);
        }
    }

    /**
     * Handle command without arguments.
     *
     * @param event
     *        The event for this command
     */
    private void onEchoCommand(GuildMessageReceivedEvent event)
    {
        // Note: None of these can be null due to our configuration with the JDABuilder!
        Member member = event.getMember();                              // Member is the context of the user for the specific guild, containing voice state and roles
        GuildVoiceState voiceState = member.getVoiceState();            // Check the current voice state of the user
        VoiceChannel channel = voiceState.getChannel();                 // Use the channel the user is currently connected to
        if (channel != null) {
            connectTo(channel);                                         // Join the channel of the user
            onConnecting(channel, event.getChannel());                  // Tell the user about our success
        }
        else
        {
            onUnknownChannel(event.getChannel(), "your voice channel"); // Tell the user about our failure
        }
    }

    /**
     * Handle command with arguments.
     *
     * @param event
     *        The event for this command
     * @param guild
     *        The guild where its happening
     * @param arg
     *        The input argument
     */
    private void onEchoCommand(GuildMessageReceivedEvent event, Guild guild, String arg)
    {
        boolean isNumber = arg.matches("\\d+"); // This is a regular expression that ensures the input consists of digits
        VoiceChannel channel = null;
        if (isNumber)                           // The input is an id?
        {
            channel = guild.getVoiceChannelById(arg);
        }
        if (channel == null)                    // Then the input must be a name?
        {
            List<VoiceChannel> channels = guild.getVoiceChannelsByName(arg, true);
            if (!channels.isEmpty())            // Make sure we found at least one exact match
                channel = channels.get(0);      // We found a channel! This cannot be null.
        }

        TextChannel textChannel = event.getChannel();
        if (channel == null)                    // I have no idea what you want mr user
        {
            onUnknownChannel(textChannel, arg); // Let the user know about our failure
            return;
        }
        connectTo(channel);                     // We found a channel to connect to!
        onConnecting(channel, textChannel);     // Let the user know, we were successful!
    }

    /**
     * Inform user about successful connection.
     *
     * @param channel
     *        The voice channel we connected to
     * @param textChannel
     *        The text channel to send the message in
     */
    private void onConnecting(VoiceChannel channel, TextChannel textChannel)
    {
        textChannel.sendMessage("Connecting to " + channel.getName()).queue(); // never forget to queue()!
    }

    /**
     * The channel to connect to is not known to us.
     *
     * @param channel
     *        The message channel (text channel abstraction) to send failure information to
     * @param comment
     *        The information of this channel
     */
    private void onUnknownChannel(MessageChannel channel, String comment)
    {
        channel.sendMessage("Unable to connect to ``" + comment + "``, no such channel!").queue(); // never forget to queue()!
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
            The sender will provide 20ms of PCM stereo audio (pass-through) once requested by JDA
            When audio is provided JDA will automatically set the bot to speaking!
         */
        private final Queue<byte[]> queue = new ConcurrentLinkedQueue<>();
//        AudioFormat audioFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000f, 32, 2, 3840, 50, true);
        final AudioFormat receivedAudioFormat = AudioReceiveHandler.OUTPUT_FORMAT;
        final AudioFormat sentAudioFormat = AudioSendHandler.INPUT_FORMAT;
        SourceDataLine sourceDataLine = null;
        TargetDataLine targetDataLine = null;
        private final int packetSize = 3840;
        byte[] tempbuffer = new byte[packetSize];


        public EchoHandler(){
            try{
                sourceDataLine = AudioSystem.getSourceDataLine(receivedAudioFormat);
//                sourceDataLine = getNamedSourceLine("PulseAudio Mixer");
                sourceDataLine.open();
                System.out.println(sourceDataLine.getFormat());
                sourceDataLine.start();
                System.out.println(receivedAudioFormat);

                System.out.println("and now for mic");

//                targetDataLine = AudioSystem.getTargetDataLine(sentAudioFormat);
//                targetDataLine = getNamedTargetLine("DirectAudioDevice");
//                AudioFormat format = new AudioFormat(48000.0f, 16, 2, true, true);
                targetDataLine = AudioSystem.getTargetDataLine(sentAudioFormat);
                targetDataLine.open(sentAudioFormat, packetSize);
                System.out.println(targetDataLine.getFormat());
                targetDataLine.start();
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

//                Thread micThread = new Thread(() -> {
//                    targetDataLine.start();
//                    byte[] bigBuffer = new byte[3840];
//                    byte[] smallBuffer = new byte[2];
//                    int offset = 0;
//
//                    long nextTime = System.currentTimeMillis();
//                    while(true){
////                        nextTime+=20;
////                        while (offset < (bigBuffer.length - 1) ){
////                            targetDataLine.read(bigBuffer, offset, 2);
//////                            System.arraycopy(smallBuffer, 0, bigBuffer, offset, smallBuffer.length);
////                            offset += 2;
////                        }
////                        offset = 0;
//                        targetDataLine.read(bigBuffer, 0, 3840);
//                        outqueue.add(bigBuffer);
//                        while(outqueue.size() >= 10){}
////                        while(System.currentTimeMillis() < nextTime){}
//                    }
//                });
//            micThread.start();
            } else {
                System.out.println("well shit");
            }

        }

//        public void mic_loop(){
//            while(true){
//                targetDataLine.read(data, 0, data.length);
//                outqueue.add(data);
//            }
//        }

        /* Receive Handling */

        @Override // combine multiple user audio-streams into a single one
        public boolean canReceiveCombined()
        {
            // limit queue to 10 entries, if that is exceeded we can not receive more until the send system catches up
//            return queue.size() < 10;
            return true;
        }

        @Override
        public void handleCombinedAudio(CombinedAudio combinedAudio)
        {
            // we only want to send data when a user actually sent something, otherwise we would just send silence
//            if (combinedAudio.getUsers().isEmpty())
//                return;

            byte[] data = combinedAudio.getAudioData(1f); // volume at 100% = 1.0 (50% = 0.5 / 55% = 0.55)
//            queue.add(data);
            sourceDataLine.write(data, 0, data.length);
        }

        /* Send Handling */
//        int offset = 0;
//        int part = 4;
//        int lastOffset = (packetSize - packetSize/part);

        @Override
        public boolean canProvide()
        {
            // If we have something in our buffer we can provide it to the send system
//            return targetDataLine.isActive();
//            int size = outqueue.size();
//            System.out.print(size);

//            targetDataLine.read(tempbuffer, offset, packetSize/part);
//            if(offset >= lastOffset){
//                outqueue.add(tempbuffer);
//                offset = 0;
//            } else {
//                offset += packetSize/part;
//            }

//            return size > 0;
            return true;
        }

        @Override
        public ByteBuffer provide20MsAudio()
        {
//            System.out.print("reading data  ");
//            targetDataLine.read(data, 0, 3840);
//            System.out.println(data.length);
//            return data == null ? null : ByteBuffer.wrap(data);
//            System.out.println(" reading data");

            targetDataLine.read(tempbuffer, 0, packetSize);
//            outqueue.add(tempbuffer);

//            byte[] data = outqueue.poll();
            return tempbuffer == null ? null : ByteBuffer.wrap(tempbuffer);
//            return data == null ? null : ByteBuffer.wrap(data);
        }

        @Override
        public boolean isOpus()
        {
            // since we send audio that is received from discord we don't have opus but PCM
            return false;
        }

        /* helper fns */

//        private SourceDataLine getNamedSourceLine(String lineName){
//            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
//            for (Mixer.Info info: mixerInfos) {
//                System.out.println(info);
//                if(info.getName().contains(lineName)){
//                    Mixer m = AudioSystem.getMixer(info);
//                    SourceDataLine sourceDataLine = null;
//                    try {
//                        sourceDataLine = (SourceDataLine)m.getLine(m.getSourceLineInfo()[0]);
//
//                    } catch (LineUnavailableException e) {
//                        e.printStackTrace();
//                    }
//                    System.out.println(m.getSourceLines().length);
//                    if (sourceDataLine != null){
//                        return sourceDataLine;
//                    }
//
//                }
//            }
//            return null;
//        }
//        private TargetDataLine getNamedTargetLine(String lineName){
//            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
//            for (Mixer.Info info: mixerInfos) {
//                System.out.println(info);
//                if(info.getName().contains(lineName)){
//                    Mixer m = AudioSystem.getMixer(info);
//                    Line.Info[] targetLineInfos = m.getTargetLineInfo();
//                    for(Line.Info targetLineInfo: targetLineInfos){
//                        System.out.println("+++"+targetLineInfo);
//                    }
//                    TargetDataLine targetDataLine = null;
//                    try {
//                        targetDataLine = (TargetDataLine)m.getLine(m.getTargetLineInfo()[0]);
//
//                    } catch (LineUnavailableException e) {
//                        e.printStackTrace();
//                    }
//                    System.out.println(m.getTargetLines().length);
//                    if (targetDataLine != null){
//                        return targetDataLine;
//                    }
//
//                }
//            }
//            return null;
//        }

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