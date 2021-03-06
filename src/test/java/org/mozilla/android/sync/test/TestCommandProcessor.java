/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.List;

import org.json.simple.parser.ParseException;
import org.junit.Test;
import org.mozilla.gecko.sync.CommandProcessor;
import org.mozilla.gecko.sync.CommandRunner;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.NonObjectJSONException;

public class TestCommandProcessor extends CommandProcessor {

  public static final String commandType = "displayURI";
  public static final String commandWithNoArgs = "{\"command\":\"displayURI\"}";
  public static final String commandWithNoType = "{\"args\":[\"https://bugzilla.mozilla.org/show_bug.cgi?id=731341\",\"PKsljsuqYbGg\"]}";
  public static final String wellFormedCommand = "{\"args\":[\"https://bugzilla.mozilla.org/show_bug.cgi?id=731341\",\"PKsljsuqYbGg\"],\"command\":\"displayURI\"}";

  private boolean commandExecuted;
  public class MockCommandRunner implements CommandRunner {
    @Override
    public void executeCommand(List<String> args) {
      commandExecuted = true;
    }
  }

  @Test
  public void testRegisterCommand() throws NonObjectJSONException, IOException, ParseException {
    assertNull(commands.get(commandType));
    this.registerCommand(commandType, new MockCommandRunner());
    assertNotNull(commands.get(commandType));
  }

  @Test
  public void testProcessRegisteredCommand() throws NonObjectJSONException, IOException, ParseException {
    commandExecuted = false;
    ExtendedJSONObject unparsedCommand = new ExtendedJSONObject(wellFormedCommand);
    this.registerCommand(commandType, new MockCommandRunner());
    this.processCommand(unparsedCommand);
    assertTrue(commandExecuted);
  }

  @Test
  public void testProcessUnregisteredCommand() throws NonObjectJSONException, IOException, ParseException {
    commandExecuted = false;
    ExtendedJSONObject unparsedCommand = new ExtendedJSONObject(wellFormedCommand);
    this.processCommand(unparsedCommand);
    assertFalse(commandExecuted);
  }

  @Test
  public void testProcessInvalidCommand() throws NonObjectJSONException, IOException, ParseException {
    ExtendedJSONObject unparsedCommand = new ExtendedJSONObject(commandWithNoType);
    this.registerCommand(commandType, new MockCommandRunner());
    this.processCommand(unparsedCommand);
    assertFalse(commandExecuted);
  }

  @Test
  public void testParseCommandNoType() throws NonObjectJSONException, IOException, ParseException {
    ExtendedJSONObject unparsedCommand = new ExtendedJSONObject(commandWithNoType);
    assertNull(this.parseCommand(unparsedCommand));
  }

  @Test
  public void testParseCommandNoArgs() throws NonObjectJSONException, IOException, ParseException {
    ExtendedJSONObject unparsedCommand = new ExtendedJSONObject(commandWithNoArgs);
    assertNull(this.parseCommand(unparsedCommand));
  }

  @Test
  public void testParseWellFormedCommand() throws NonObjectJSONException, IOException, ParseException {
    ExtendedJSONObject unparsedCommand = new ExtendedJSONObject(wellFormedCommand);
    Command parsedCommand = this.parseCommand(unparsedCommand);
    assertNotNull(parsedCommand);
    assertEquals(2, parsedCommand.args.size());
    assertEquals(commandType, parsedCommand.commandType);
  }
}
