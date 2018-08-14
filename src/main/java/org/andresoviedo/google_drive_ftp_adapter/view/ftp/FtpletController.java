package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.apache.ftpserver.ftplet.*;

import java.util.HashMap;
import java.util.Map;

public class FtpletController implements Ftplet {

    private static Map<String, Class<? extends Authority>> commandAuthorityMap = new HashMap<>();

    static {
        commandAuthorityMap.put("PWD", Authorities.PWDPermission.class);
        commandAuthorityMap.put("CWD", Authorities.CWDPermission.class);
        commandAuthorityMap.put("LIST", Authorities.ListPermission.class);
        commandAuthorityMap.put("STOR", Authorities.StorePermission.class);
        commandAuthorityMap.put("DELE", Authorities.DeletePermission.class);
        commandAuthorityMap.put("RETR", Authorities.RetrievePermission.class);
        commandAuthorityMap.put("RMD", Authorities.RemoveDirPermission.class);
        commandAuthorityMap.put("MKD", Authorities.MakeDirPermission.class);
        commandAuthorityMap.put("APPE", Authorities.AppendPermission.class);
        commandAuthorityMap.put("RNFR", Authorities.RenameToPermission.class);
        commandAuthorityMap.put("RNTO", Authorities.RenameToPermission.class);
    }

    @Override
    public FtpletResult afterCommand(FtpSession arg0, FtpRequest arg1, FtpReply arg2) {
        return null;
    }

    @Override
    public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException {
        if (session.getUser() != null) {
            Class<? extends Authority> authority = commandAuthorityMap.get(request.getCommand());
            if (authority != null && session.getUser().getAuthorities(authority).isEmpty()) {
                session.write(new DefaultFtpReply(FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN, "Permission denied"));
                return FtpletResult.SKIP;
            }
        }
        return null;
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FtpletContext arg0) {
    }

    @Override
    public FtpletResult onConnect(FtpSession arg0) {
        return null;
    }

    @Override
    public FtpletResult onDisconnect(FtpSession arg0) {
        return null;
    }

}
