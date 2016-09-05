/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.decompiler.main.decompiler;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.*;
import java.util.*;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ConsoleDecompiler implements IBytecodeProvider, IResultSaver {

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    if (args.length < 2) {
      System.out.println(
              "Usage: java -jar fernflower.jar [-<option>=<value>]* [<source>]+ <destination>\n" +
                      "Example: java -jar fernflower.jar -dgs=true c:\\my\\source\\ c:\\my.jar d:\\decompiled\\\n" +
                      "* means 0 or more times\n" +
                      "+ means 1 or more times\n" +
                      "\n" +
                      "<source>: file or directory with files to be decompiled. Directories are recursively scanned. Allowed file extensions are class, zip and jar.\n" +
                      "          Sources prefixed with -e= mean \"library\" files that won't be decompiled, but taken into account when analysing relationships between \n" +
                      "          classes or methods. Especially renaming of identifiers (s. option 'ren') can benefit from information about external classes.          \n" +
                      "<destination>: destination directory \n" +
                      "<option>,<value>: command line option with the corresponding value, see 4.\n" +
                      "\n" +
                      "\n" +
                      "Examples:\n" +
                      "\n" +
                      "java -jar fernflower.jar -hes=0 -hdc=0 c:\\Temp\\binary\\ -e=c:\\Java\\rt.jar c:\\Temp\\source\\\n" +
                      "\n" +
                      "java -jar fernflower.jar -dgs=1 c:\\Temp\\binary\\library.jar c:\\Temp\\binary\\Boot.class c:\\Temp\\source\\\n" +
                      "\n" +
                      "\n" +
                      "4. Command line options\n" +
                      "\n" +
                      "With the exception of mpm and urc the value of 1 means the option is activated, 0 - deactivated. Default \n" +
                      "value, if any, is given between parentheses.\n" +
                      "\n" +
                      "Typically, the following options will be changed by user, if any: hes, hdc, dgs, mpm, ren, urc \n" +
                      "The rest of options can be left as they are: they are aimed at professional reverse engineers.\n" +
                      "\n" +
                      "rbr (1): hide bridge methods\n" +
                      "rsy (0): hide synthetic class members\n" +
                      "din (1): decompile inner classes\n" +
                      "dc4 (1): collapse 1.4 class references\n" +
                      "das (1): decompile assertions\n" +
                      "hes (1): hide empty super invocation\n" +
                      "hdc (1): hide empty default constructor\n" +
                      "dgs (0): decompile generic signatures\n" +
                      "ner (1): assume return not throwing exceptions\n" +
                      "den (1): decompile enumerations\n" +
                      "rgn (1): remove getClass() invocation, when it is part of a qualified new statement\n" +
                      "lit (0): output numeric literals \"as-is\"\n" +
                      "asc (0): encode non-ASCII characters in string and character literals as Unicode escapes\n" +
                      "bto (1): interpret int 1 as boolean true (workaround to a compiler bug)\n" +
                      "nns (1): allow for not set synthetic attribute (workaround to a compiler bug)\n" +
                      "uto (1): consider nameless types as java.lang.Object (workaround to a compiler architecture flaw)\n" +
                      "udv (1): reconstruct variable names from debug information, if present\n" +
                      "rer (1): remove empty exception ranges\n" +
                      "fdi (1): de-inline finally structures\n" +
                      "mpm (0): maximum allowed processing time per decompiled method, in seconds. 0 means no upper limit\n" +
                      "ren (0): rename ambiguous (resp. obfuscated) classes and class elements\n" +
                      "urc    : full name of user-supplied class implementing IIdentifierRenamer. It is used to determine which class identifiers\n" +
                      "         should be renamed and provides new identifier names. For more information see section 5\n" +
                      "inn (1): check for IntelliJ IDEA-specific @NotNull annotation and remove inserted code if found\n" +
                      "lac (0): decompile lambda expressions to anonymous classes\n" +
                      "nls (0): define new line character to be used for output. 0 - '\\r\\n' (Windows), 1 - '\\n' (Unix), default is OS-dependent\n" +
                      "ind    : indentation string (default is \"   \" (3 spaces))\n" +
                      " \n" +
                      "The default logging level is INFO. This value can be overwritten by setting the option 'log' as follows:\n" +
                      "log (INFO): possible values TRACE, INFO, WARN, ERROR\n" +
                      "\n" +
                      "\n" +
                      "5. Renaming identifiers\n" +
                      "\n" +
                      "Some obfuscators give classes and their member elements short, meaningless and above all ambiguous names. Recompiling of such\n" +
                      "code leads to a great number of conflicts. Therefore it is advisable to let the decompiler rename elements in its turn, \n" +
                      "ensuring uniqueness of each identifier.\n" +
                      "\n" +
                      "Option 'ren' (i.e. -ren=1) activates renaming functionality. Default renaming strategy goes as follows:\n" +
                      "- rename an element if its name is a reserved word or is shorter than 3 characters\n" +
                      "- new names are built according to a simple pattern: (class|method|field)_<consecutive unique number>  \n" +
                      "You can overwrite this rules by providing your own implementation of the 4 key methods invoked by the decompiler while renaming. Simply \n" +
                      "pass a class that implements org.jetbrains.java.decompiler.main.extern.IIdentifierRenamer in the option 'urc'\n" +
                      "(e.g. -urc=com.mypackage.MyRenamer) to Fernflower. The class must be available on the application classpath.\n" +
                      "\n" +
                      "The meaning of each method should be clear from naming: toBeRenamed determine whether the element will be renamed, while the other three\n" +
                      "provide new names for classes, methods and fields respectively.\n");
      return;
    }

    Map<String, Object> mapOptions = new HashMap<String, Object>();
    List<File> lstSources = new ArrayList<File>();
    List<File> lstLibraries = new ArrayList<File>();

    boolean isOption = true;
    for (int i = 0; i < args.length - 1; ++i) { // last parameter - destination
      String arg = args[i];

      if (isOption && arg.length() > 5 && arg.charAt(0) == '-' && arg.charAt(4) == '=') {
        String value = arg.substring(5);
        if ("true".equalsIgnoreCase(value)) {
          value = "1";
        }
        else if ("false".equalsIgnoreCase(value)) {
          value = "0";
        }

        mapOptions.put(arg.substring(1, 4), value);
      }
      else {
        isOption = false;

        if (arg.startsWith("-e=")) {
          addPath(lstLibraries, arg.substring(3));
        }
        else {
          addPath(lstSources, arg);
        }
      }
    }

    if (lstSources.isEmpty()) {
      System.out.println("error: no sources given");
      return;
    }

    File destination = new File(args[args.length - 1]);
    if (!destination.isDirectory()) {
      System.out.println("error: destination '" + destination + "' is not a directory");
      return;
    }

    PrintStreamLogger logger = new PrintStreamLogger(System.out);
    ConsoleDecompiler decompiler = new ConsoleDecompiler(destination, mapOptions, logger);

    for (File source : lstSources) {
      decompiler.addSpace(source, true);
    }
    for (File library : lstLibraries) {
      decompiler.addSpace(library, false);
    }

    decompiler.decompileContext();
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void addPath(List<File> list, String path) {
    File file = new File(path);
    if (file.exists()) {
      list.add(file);
    }
    else {
      System.out.println("warn: missing '" + path + "', ignored");
    }
  }

  // *******************************************************************
  // Implementation
  // *******************************************************************

  private final File root;
  private final Fernflower fernflower;
  private final Map<String, ZipOutputStream> mapArchiveStreams = new HashMap<String, ZipOutputStream>();
  private final Map<String, Set<String>> mapArchiveEntries = new HashMap<String, Set<String>>();

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public ConsoleDecompiler(File destination, Map<String, Object> options) {
    this(destination, options, new PrintStreamLogger(System.out));
  }

  protected ConsoleDecompiler(File destination, Map<String, Object> options, IFernflowerLogger logger) {
    root = destination;
    fernflower = new Fernflower(this, this, options, logger);
  }

  public void addSpace(File file, boolean isOwn) {
    fernflower.getStructContext().addSpace(file, isOwn);
  }

  public void decompileContext() {
    try {
      fernflower.decompileContext();
    }
    finally {
      fernflower.clearContext();
    }
  }

  // *******************************************************************
  // Interface IBytecodeProvider
  // *******************************************************************

  @Override
  public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
    File file = new File(externalPath);
    if (internalPath == null) {
      return InterpreterUtil.getBytes(file);
    }
    else {
      ZipFile archive = new ZipFile(file);
      try {
        ZipEntry entry = archive.getEntry(internalPath);
        if (entry == null) {
          throw new IOException("Entry not found: " + internalPath);
        }
        return InterpreterUtil.getBytes(archive, entry);
      }
      finally {
        archive.close();
      }
    }
  }

  // *******************************************************************
  // Interface IResultSaver
  // *******************************************************************

  private String getAbsolutePath(String path) {
    return new File(root, path).getAbsolutePath();
  }

  @Override
  public void saveFolder(String path) {
    File dir = new File(getAbsolutePath(path));
    if (!(dir.mkdirs() || dir.isDirectory())) {
      throw new RuntimeException("Cannot create directory " + dir);
    }
  }

  @Override
  public void copyFile(String source, String path, String entryName) {
    try {
      InterpreterUtil.copyFile(new File(source), new File(getAbsolutePath(path), entryName));
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
    }
  }

  @Override
  public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
    File file = new File(getAbsolutePath(path), entryName);
    try {
      Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF8");
      try {
        out.write(content);
      }
      finally {
        out.close();
      }
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot write class file " + file, ex);
    }
  }

  @Override
  public void createArchive(String path, String archiveName, Manifest manifest) {
    File file = new File(getAbsolutePath(path), archiveName);
    try {
      if (!(file.createNewFile() || file.isFile())) {
        throw new IOException("Cannot create file " + file);
      }

      FileOutputStream fileStream = new FileOutputStream(file);
      @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
      ZipOutputStream zipStream = manifest != null ? new JarOutputStream(fileStream, manifest) : new ZipOutputStream(fileStream);
      mapArchiveStreams.put(file.getPath(), zipStream);
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot create archive " + file, ex);
    }
  }

  @Override
  public void saveDirEntry(String path, String archiveName, String entryName) {
    saveClassEntry(path, archiveName, null, entryName, null);
  }

  @Override
  public void copyEntry(String source, String path, String archiveName, String entryName) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();

    if (!checkEntry(entryName, file)) {
      return;
    }

    try {
      ZipFile srcArchive = new ZipFile(new File(source));
      try {
        ZipEntry entry = srcArchive.getEntry(entryName);
        if (entry != null) {
          InputStream in = srcArchive.getInputStream(entry);
          ZipOutputStream out = mapArchiveStreams.get(file);
          out.putNextEntry(new ZipEntry(entryName));
          InterpreterUtil.copyStream(in, out);
          in.close();
        }
      }
      finally {
        srcArchive.close();
      }
    }
    catch (IOException ex) {
      String message = "Cannot copy entry " + entryName + " from " + source + " to " + file;
      DecompilerContext.getLogger().writeMessage(message, ex);
    }
  }

  @Override
  public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();

    if (!checkEntry(entryName, file)) {
      return;
    }

    try {
      ZipOutputStream out = mapArchiveStreams.get(file);
      out.putNextEntry(new ZipEntry(entryName));
      if (content != null) {
        out.write(content.getBytes("UTF-8"));
      }
    }
    catch (IOException ex) {
      String message = "Cannot write entry " + entryName + " to " + file;
      DecompilerContext.getLogger().writeMessage(message, ex);
    }
  }

  private boolean checkEntry(String entryName, String file) {
    Set<String> set = mapArchiveEntries.get(file);
    if (set == null) {
      mapArchiveEntries.put(file, set = new HashSet<String>());
    }

    boolean added = set.add(entryName);
    if (!added) {
      String message = "Zip entry " + entryName + " already exists in " + file;
      DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
    }
    return added;
  }

  @Override
  public void closeArchive(String path, String archiveName) {
    String file = new File(getAbsolutePath(path), archiveName).getPath();
    try {
      mapArchiveEntries.remove(file);
      mapArchiveStreams.remove(file).close();
    }
    catch (IOException ex) {
      DecompilerContext.getLogger().writeMessage("Cannot close " + file, IFernflowerLogger.Severity.WARN);
    }
  }
}
