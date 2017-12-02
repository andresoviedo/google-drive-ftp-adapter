package org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.AppendPermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.CWDPermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.DeletePermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.ListPermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.MakeDirPermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.PWDPermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.RemoveDirPermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.RenameToPermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.RetrievePermission;
import org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp.Authorities.StorePermission;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.DefaultFtpReply;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;

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
