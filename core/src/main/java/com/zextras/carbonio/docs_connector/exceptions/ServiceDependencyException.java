package com.zextras.carbonio.docs_connector.exceptions;

public class ServiceDependencyException extends Exception {

  public ServiceDependencyException(Throwable throwable) {
    super(throwable.getMessage(), throwable);
  }
}
