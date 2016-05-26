package com.smxemail.rangeresolver.lang;

import org.eclipse.aether.transfer.AbstractTransferListener;
import org.eclipse.aether.transfer.TransferCancelledException;
import org.eclipse.aether.transfer.TransferEvent;

import java.util.function.Consumer;

public class ConsoleTransferListener extends AbstractTransferListener {

  Consumer<String> infoLogger;

  public ConsoleTransferListener(Consumer<String> infoLogger) {
    this.infoLogger = infoLogger;
  }

  @Override
  public void transferCorrupted(TransferEvent transferEvent) throws TransferCancelledException {
    infoLogger.accept(String.format("Corrupted download: %s", transferEvent.getResource().getResourceName()));
  }

  @Override
  public void transferFailed(TransferEvent transferEvent) {
    infoLogger.accept(String.format("Unable to download: %s", transferEvent.getResource().getResourceName()));
  }
}
