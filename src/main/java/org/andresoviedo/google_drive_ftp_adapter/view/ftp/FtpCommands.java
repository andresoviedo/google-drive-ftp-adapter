package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.apache.ftpserver.command.AbstractCommand;
import org.apache.ftpserver.ftplet.FtpFile;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.impl.FtpIoSession;
import org.apache.ftpserver.impl.FtpServerContext;
import org.apache.ftpserver.impl.LocalizedFtpReply;
import org.apache.ftpserver.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.util.Date;

public class FtpCommands {
    public static class MFMT extends AbstractCommand {

        private final Logger LOG = LoggerFactory.getLogger(MFMT.class);

        /**
         * Execute command.
         */
        public void execute(final FtpIoSession session, final FtpServerContext context, final FtpRequest request) {

            // reset state variables
            session.resetState();

            String argument = request.getArgument();

            if (argument == null || argument.trim().length() == 0) {
                session.write(LocalizedFtpReply.translate(session, request, context,
                        FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS, "MFMT.invalid", null));
                return;
            }

            String[] arguments = argument.split(" ", 2);

            if (arguments.length != 2) {
                session.write(LocalizedFtpReply.translate(session, request, context,
                        FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS, "MFMT.invalid", null));
                return;
            }

            String timestamp = arguments[0].trim();

            try {

                Date time = DateUtils.parseFTPDate(timestamp);

                String fileName = arguments[1].trim();

                // get file object
                FtpFile file = null;

                try {
                    file = session.getFileSystemView().getFile(fileName);
                } catch (Exception ex) {
                    LOG.debug("Exception getting the file object: " + fileName, ex);
                }

                if (file == null || !file.doesExist()) {
                    session.write(LocalizedFtpReply.translate(session, request, context, FtpReply.REPLY_550_REQUESTED_ACTION_NOT_TAKEN,
                            "MFMT.filemissing", fileName));
                    return;
                }

                // INFO: We want folders also to be touched
                // // check file
                // if (!file.isFile()) {
                // session.write(LocalizedFtpReply
                // .translate(
                // session,
                // request,
                // context,
                // FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS,
                // "MFMT.invalid", null));
                // return;
                // }

                // check if we can set date and retrieve the actual date
                // stored
                // for the file.
                if (!file.setLastModified(time.getTime())) {
                    // we couldn't set the date, possibly the file was
                    // locked
                    session.write(LocalizedFtpReply.translate(session, request, context,
                            FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN, "MFMT", fileName));
                    return;
                }

                // all checks okay, lets go
                session.write(LocalizedFtpReply.translate(session, request, context, FtpReply.REPLY_213_FILE_STATUS, "MFMT",
                        "ModifyTime=" + timestamp + "; " + fileName));

            } catch (ParseException e) {
                session.write(LocalizedFtpReply.translate(session, request, context,
                        FtpReply.REPLY_501_SYNTAX_ERROR_IN_PARAMETERS_OR_ARGUMENTS, "MFMT.invalid", null));
            }

        }
    }
}
