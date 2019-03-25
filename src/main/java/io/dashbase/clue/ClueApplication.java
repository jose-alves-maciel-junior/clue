package io.dashbase.clue;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import io.dashbase.clue.client.CmdlineHelper;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.store.Directory;

import io.dashbase.clue.commands.ClueCommand;
import io.dashbase.clue.commands.HelpCommand;

public class ClueApplication {
  
  private final ClueContext ctx;
  private final Optional<ClueCommand> helpCommand;
  
  private static ClueAppConfiguration config;

  private CmdlineHelper cmdlineHelper;
  
  static {
    try {
      config = ClueAppConfiguration.load();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public ClueContext getContext() {
    return ctx;
  }
  
  public ClueAppConfiguration getConfiguration() {
    return config;
  }
  
  public ClueContext newContext(String dir, ClueAppConfiguration config, boolean interactiveMode) throws Exception {
    return new ClueContext(dir, config, interactiveMode);
  }
  
  public ClueApplication(String idxLocation, boolean interactiveMode) throws Exception{
    ctx = newContext(idxLocation, config, interactiveMode);

    if (!DirectoryReader.indexExists(ctx.getDirectory())){
      System.out.println("lucene index does not exist at: "+idxLocation);
      System.exit(1);
    }

    helpCommand = ctx.getCommand(HelpCommand.CMD_NAME);
  }
  
  public void handleCommand(String cmdName, String[] args, PrintStream out){
    Optional<ClueCommand> cmd = ctx.getCommand(cmdName);
    if (!cmd.isPresent()){
      out.println(cmdName+" is not supported:");
      cmd = helpCommand;
    }
    try{
      cmd.get().execute(args, out);
    }
    catch(Exception e){
      e.printStackTrace();
    }
  }

  public void run() throws IOException {
    CmdlineHelper helper = new CmdlineHelper(new Supplier<Collection<String>>() {
      @Override
      public Collection<String> get() {
        return ctx.getCommandRegistry().commandNames();
      }
    }, new Supplier<Collection<String>>() {
      @Override
      public Collection<String> get() {
        return ctx.fieldNames();
      }
    });

    while(true){
      String line = helper.readCommand();
      if (line == null || line.isEmpty()) continue;
      line = line.trim();
      if ("exit".equals(line)) {
        return;
      }
      String[] parts = line.split("\\s");
      if (parts.length > 0){
        String cmd = parts[0];
        String[] cmdArgs = new String[parts.length - 1];
        System.arraycopy(parts, 1, cmdArgs, 0, cmdArgs.length);
        handleCommand(cmd, cmdArgs, System.out);
      }
    }
  }
  
  public void shutdown() throws Exception {
    ctx.shutdown();
  }
  
  
  public static void main(String[] args) throws Exception {
    if (args.length < 1){
      System.out.println("usage: <index location> <command> <command args>");
      System.exit(1);
    }
        
    String idxLocation = args[0];
    
    final ClueApplication app;
    
    if (args.length > 1){
      String cmd = args[1];
      if ("readonly".equalsIgnoreCase(cmd)) {
        if (args.length > 2) {
          cmd = args[2];
          app = new ClueApplication(idxLocation, false);
          String[] cmdArgs;
          cmdArgs = new String[args.length - 3];
          System.arraycopy(args, 3, cmdArgs, 0, cmdArgs.length);
          app.ctx.setReadOnlyMode(true);
          app.handleCommand(cmd, cmdArgs, System.out);
          app.shutdown();
        }
      }
      else {
        app = new ClueApplication(idxLocation, false);
        String[] cmdArgs;
        cmdArgs = new String[args.length - 2];
        System.arraycopy(args, 2, cmdArgs, 0, cmdArgs.length);
        app.handleCommand(cmd, cmdArgs, System.out);
        app.shutdown();
      }
      return;
    }
    app = new ClueApplication(idxLocation, true);
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        try {
          app.shutdown();
        } catch (Exception e) {
          e.printStackTrace();
        } 
      }
    });
    app.run();
  }
}
