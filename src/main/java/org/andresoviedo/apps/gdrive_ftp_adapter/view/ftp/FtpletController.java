package org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp;

import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.*;
import org.apache.ftpserver.ftplet.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class FtpletController implements Ftplet{
	
	private static Map<String,Class<? extends Authority>> commandAuthorityMap = new HashMap<>();
	static{
		commandAuthorityMap.put("PWD", PWDPermission.class);
		commandAuthorityMap.put("CWD", CWDPermission.class);
		commandAuthorityMap.put("LIST", ListPermission.class);
		commandAuthorityMap.put("STOR", StorePermission.class);
		commandAuthorityMap.put("DELE", DeletePermission.class);
		commandAuthorityMap.put("RETR", RetrievePermission.class);
		commandAuthorityMap.put("RMD", RemoveDirPermission.class);
		commandAuthorityMap.put("MKD", MakeDirPermission.class);
		commandAuthorityMap.put("APPE", AppendPermission.class);
		commandAuthorityMap.put("RNFR", RenameToPermission.class);
		commandAuthorityMap.put("RNTO", RenameToPermission.class);
	}

	@Override
	public FtpletResult afterCommand(FtpSession arg0, FtpRequest arg1, FtpReply arg2) throws FtpException, IOException {
		return null;
	}

	@Override
	public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException, IOException {
		if (session.getUser() != null){
			Class<? extends Authority> authority = commandAuthorityMap.get(request.getCommand());
			if (authority != null && session.getUser().getAuthorities(authority).isEmpty()){
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
	public void init(FtpletContext arg0) throws FtpException {
	}

	@Override
	public FtpletResult onConnect(FtpSession arg0) throws FtpException, IOException {
		return null;
	}

	@Override
	public FtpletResult onDisconnect(FtpSession arg0) throws FtpException, IOException {
		return null;
	}

}
