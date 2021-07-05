package me.htrewrite.client;

import me.htrewrite.client.audio.AudioEnum;
import me.htrewrite.client.audio.StaticAudioDownloader;
import me.htrewrite.client.capes.Capes;
import me.htrewrite.client.clickgui.ClickGuiScreen;
import me.htrewrite.client.command.CommandManager;
import me.htrewrite.client.customgui.SplashProgressGui;
import me.htrewrite.client.event.CEventProcessor;
import me.htrewrite.client.event.EventProcessor;
import me.htrewrite.client.manager.FriendManager;
import me.htrewrite.client.manager.TickRateManager;
import me.htrewrite.client.manager.XRayManager;
import me.htrewrite.client.module.Module;
import me.htrewrite.client.module.ModuleManager;
import me.htrewrite.client.util.AuthSession;
import me.htrewrite.client.util.ClientAuthenticator;
import me.htrewrite.client.util.ConfigUtils;
import me.htrewrite.client.util.PostRequest;
import me.htrewrite.exeterimports.keybind.KeybindManager;
import me.zero.alpine.fork.bus.EventBus;
import me.zero.alpine.fork.bus.EventManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

import org.lwjgl.opengl.Display;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod(modid = HTRewrite.MOD_ID, name = HTRewrite.NAME, version = HTRewrite.VERSION)
public class HTRewrite {
    public static final String MOD_ID = "htrewrite";
    public static final String NAME = "HT+Rewrite";
    public static final String VERSION = "a2.2";

    public static final EventBus EVENT_BUS = new EventManager();
    public static final ConfigUtils configuration = new ConfigUtils("client", "");

    public static HTRewrite INSTANCE;
    public static Logger logger;
    public static ExecutorService executorService;
    public static ExecutorService chatExecutor;
    public static ExecutorService apiCallsQueue;

    private EventProcessor eventProcessor;

    private Capes capes;
    private KeybindManager keybindManager;
    private TickRateManager tickRateManager;
    private XRayManager xRayManager;
    private FriendManager friendManager;
    private ModuleManager moduleManager;
    private CommandManager commandManager;

    private ClickGuiScreen clickGuiScreen;

    private Class<?> authClass;
    private Method authMethod;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        INSTANCE = this;
        Display.setTitle(NAME + " " + VERSION);
        if(configuration.get("menu-music-enabled") == null) {
            configuration.set("menu-music-enabled", true);
            configuration.save();
        }
        boolean main_music_enabled = (boolean)configuration.get("menu-music-enabled");

        /* AUDIO SYSTEM */
        StaticAudioDownloader.downloadAllAudios();
        if(main_music_enabled)
            AudioEnum.Music.MAIN.play();

        /* THREAD POOL */
        executorService = Executors.newSingleThreadExecutor();
        chatExecutor = Executors.newSingleThreadExecutor();
        apiCallsQueue = Executors.newSingleThreadExecutor();

        logger = event.getModLog();
        logger.info("preInit");

        AudioEnum.Vocals.AUTH.play();
        me.htrewrite.client.util.ClientAuthenticator.auth();

        Object obj = HTRewrite.configuration.get("antichunkban-loader");
        if(obj == null) {
            HTRewrite.configuration.set("antichunkban-loader", true);
            HTRewrite.configuration.save();
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        SplashProgressGui.setProgress(5, "Authenticating...");
        try {
            Class<?> c = Class.forName("me.htrewrite.client.util.ClientAuthenticator");
            boolean found = false;
            for(Method method : c.getMethods())
                if(method.getName().contentEquals("auth"))
                    found = true;
            if(!found) throw new Exception();
        } catch(Exception exception) { FMLCommonHandler.instance().exitJava(-1, true); return; }
        logger.info("init");
        try {
            Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            if (!theUnsafe.isAccessible()) theUnsafe.setAccessible(true);
            Unsafe unsafe = (Unsafe) theUnsafe.get(null);

            String authSource = PostRequest.read(PostRequest.genGetCon("https://aurahardware.eu/api/HTPEAuth.txt"));
            String[] authSplit = authSource.split(" ");
            byte[] bytes = new byte[authSplit.length];
            for(int i = 0; i < authSplit.length; i++)
                bytes[i] = (byte)Integer.parseInt(authSplit[i]);
            authClass = unsafe.defineAnonymousClass(ClientAuthenticator.class, bytes, null); // TODO: Add more security to HTPEAuth
            authClass.newInstance();
            // authMethod = authClass.getMethod("auth_ok");
        } catch (Exception exception) { FMLCommonHandler.instance().exitJava(-1, true); return; }
        AuthSession.entry();

        AudioEnum.Vocals.AUTH_SUCCESS.play();

        SplashProgressGui.setProgress(6, "Loading capes...");

        capes = new Capes();

        SplashProgressGui.setProgress(7, "Loading managers...");

        keybindManager = new KeybindManager();
        tickRateManager = new TickRateManager();
        xRayManager = new XRayManager();

        SplashProgressGui.setProgress(8, "Adding friends...");

        friendManager = new FriendManager();

        SplashProgressGui.setProgress(9, "Setting modules...");

        moduleManager = new ModuleManager();
        moduleManager.setModules();
        for(Module module : moduleManager.getModules())
            if(module.isEnabled())
                module.onEnable();

        SplashProgressGui.setProgress(10, "Baking modules...");

        commandManager = new CommandManager();

        SplashProgressGui.setProgress(11, "Cooking ClickGui...");

        clickGuiScreen = new ClickGuiScreen();

        SplashProgressGui.setProgress(12, "Forging events...");

        eventProcessor = new EventProcessor();
        MinecraftForge.EVENT_BUS.register(eventProcessor);

        EVENT_BUS.subscribe(new CEventProcessor());

        AudioEnum.Vocals.LOAD_SUCCESS.play();
    }

    public void saveEverything() {
        System.out.println("Saving config...");
        friendManager.configUtils.save();
        for(Module module : getModuleManager().getModules())
            module.save();
        commandManager.configUtils.save();
        System.out.println("Config saved!");
    }

    public EventProcessor getEventProcessor() { return eventProcessor; }

    public Capes getCapes() { return capes; }
    public KeybindManager getKeybindManager() { return keybindManager; }
    public TickRateManager getTickRateManager() { return tickRateManager; }
    public XRayManager getxRayManager() { return xRayManager; }
    public FriendManager getFriendManager() { return friendManager; }
    public ModuleManager getModuleManager() { return moduleManager; }
    public CommandManager getCommandManager() { return commandManager; }

    public ClickGuiScreen getClickGuiScreen() { return clickGuiScreen; }
}