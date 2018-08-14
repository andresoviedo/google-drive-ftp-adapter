package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class GFtpServerFactoryTest {

    private final Character[] ILLEGAL_CHARACTERS = {'/', '\n', '\r', '\t', '\0', '\f', '`', '?', '*', '\\', '<', '>',
            '|', '\"', ':'};

    private final String regex = "\\/|[\\x00-\\x1F\\x7F]|\\`|\\?|\\*|\\\\|\\<|\\>|\\||\\\"|\\:";

    private final Pattern illegalCharsPattern = Pattern.compile(regex);

    @Test
    public void testIllegalChars() {

        for (Character character : ILLEGAL_CHARACTERS) {
            Assert.assertTrue("Character '" + character + "' didn't matched the regexp",
                    illegalCharsPattern.matcher(character.toString()).find());
        }
    }

    @Test
    public void testReplaceIllegalChars() {
        List<String> illegalFilenames = Arrays.asList("nombre de fichero normal.txt", "hola\\0", "que\\ttal",
                "what`s up?<>");

        for (String illegalFilename : illegalFilenames) {
            illegalFilename = illegalCharsPattern.matcher(illegalFilename).replaceAll("");
            System.out.println("[" + illegalFilename + "]");
            Assert.assertFalse("Filename '" + illegalFilename + "' matched illegal regexp", illegalCharsPattern
                    .matcher(illegalFilename).find());
        }
    }

}
