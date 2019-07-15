package pro.delfik.vimebot.virtualplayer;

import pro.delfik.vimebot.AutomaticUnit;
import pro.delfik.vimebot.Bot;
import pro.delfik.vimebot.BotUtil;
import pro.delfik.vimebot.command.Command;
import pro.delfik.vimebot.command.CommandPattern;
import pro.delfik.vimebot.command.LogInterceptor;

import java.awt.Robot;
import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ето добрый робот
 * Он умеет выполнять ваши пожелания
 * Он очень стараицо
 * Ещё он умеет читать чат и сообщать вам всю информацию оттуда
 */
public abstract class VirtualPlayer extends AutomaticUnit {
    
    /**
     * Перехватчик логов, нужен для чтения чата
     */
    private final LogInterceptor logInterceptor;
    
    /**
     * Асинхронная очередь из сообщений
     * Бот берёт их отсюда и выполняет одно за другим
     */
    private final Queue<Command> commands = new ConcurrentLinkedQueue<>();
    
    /**
     * Когда какая-то команда выполняется, бот ждёт сообщения в чат,
     * которое будет соответствовать паттерну результата команды.
     * Здесь хранится ожидающая команда.
     */
    private volatile Command currentCommand;
    
    /**
     * Аналог currentCommand != null
     */
    private volatile boolean lock;
    
    /**
     * Ссылка на робота для удобства использования
     */
    protected final Robot robot;
    
    /**
     * Инициализация бота с игнорированием логов.
     * @param bot Ссылка на бота
     */
    public VirtualPlayer(Bot bot) {
        super(bot, "Virtual Player");
        this.logInterceptor = null;
        this.robot = bot.getRobot();
    }
    
    /**
     * Инициализация бота с чтением логов
     *
     * @param bot Ссылка на бота
     * @param logFile        Файл, в который пишутся логи
     * @param logFilter      Фильтр для логов
     * @param loggerCallback Слушатель сообщений (Может быть null)
     */
    public VirtualPlayer(Bot bot, File logFile, Function<String, String> logFilter, Consumer<String> loggerCallback) {
        super(bot, "Virtual Player");
        this.robot = bot.getRobot();
        if (logFile == null)
            logInterceptor = null;
        else
            logInterceptor = new LogInterceptor(bot, logFile, logFilter, s -> handleLog(s, loggerCallback));
    }
    
    /**
     * Передать сообщение коллбэку и подтвердить выполнение команды,
     * если сообщение соответствует паттерну её результата
     * @param s Строчка логов
     * @param loggerCallback Делегат
     */
    private void handleLog(String s, Consumer<String> loggerCallback) {
        if (loggerCallback != null)
            loggerCallback.accept(s);
        if (currentCommand == null)
            return;
        if (!currentCommand.getPattern().responseMatches(s))
            return;
        
        lock = false;
        if (currentCommand.getCallback() != null)
            currentCommand.getCallback().accept(currentCommand);
        currentCommand = null;
    }
    
    /**
     * Запустить робота и перехватчик логов (Если есть) в отдельных потоках.
     */
    @Override
    public synchronized void start() {
        super.start();
    }
    
    /**
     * Обрабатывает команды из очереди.
     */
    @Override
    public void run() {
        while (BotUtil.sleep(850)) {
            if (lock && logInterceptor != null) continue;
            Command cmd = commands.poll();
            if (cmd == null) continue;
            currentCommand = cmd;
            execute(cmd.getLine());
            lock = logInterceptor != null;
        }
    }
    
    /**
     * Добавить команду в очередь на исполнение.
     * @param pattern Паттерн команды по которому будет производится просмотр результата
     * @param callback Кастомный коллбэк специально для этой команды (Можно null)
     * @param args Аргументы, которые будут переданы в паттерн
     */
    public void queueCommand(CommandPattern pattern, Consumer<Command> callback, String... args) {
        commands.offer(new Command(pattern, callback, args));
    }
    
    /**
     * Добавить команду в очередь на исполнение
     *
     * @param command Строка для отправки
     * @deprecated Используйте queueCommand(pattern, callback, args) для безопасности
     */
    @Deprecated
    public void queueCommand(String command) {
        queueCommand(CommandPattern.IGNORE_RESPONSE, null, command);
    }
    
    /**
     * Отправить команду в чат прямо сейчас.
     *
     * @param command Строка для отправки
     */
    protected abstract void execute(String command);
    
    /**
     * Выход из режима ожидания ответа от сервера и
     * переход к выполнению следующей команды из очереди.
     */
    public void unlock() {
        lock = false;
    }
    
}