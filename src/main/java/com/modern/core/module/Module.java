package com.modern.core.module;

public interface Module {

  String id();

  void enable();

  void disable();

  void reload();
}
