package org.andresoviedo.apps.gdrive_ftp_adapter.view.ftp;

import java.io.IOException;

import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.FtpReply;
import org.apache.ftpserver.ftplet.FtpRequest;
import org.apache.ftpserver.ftplet.FtpSession;
import org.apache.ftpserver.ftplet.Ftplet;
import org.apache.ftpserver.ftplet.FtpletContext;
import org.apache.ftpserver.ftplet.FtpletResult;

public class FtpletController implements Ftplet{

	@Override
	public FtpletResult afterCommand(FtpSession arg0, FtpRequest arg1, FtpReply arg2) throws FtpException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FtpletResult beforeCommand(FtpSession session, FtpRequest request) throws FtpException, IOException {
		// TODO Auto-generated method stub
		if (session.getUser() != null){
			if (request.getCommand().equals("LIST") && session.getUser().getAuthorities(ListPermission.class).isEmpty()){
				session.write(new FtpReply(){

					@Override
					public int getCode() {
						return FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN;
					}

					@Override
					public String getMessage() {
						return "Permission denied";
					}
					
					public String toString(){
						return FtpReply.REPLY_450_REQUESTED_FILE_ACTION_NOT_TAKEN+" Permission denied";
					}				
				});
				return FtpletResult.SKIP;
			}
		}
		return null;
	}

	@Override
	public void destroy() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void init(FtpletContext arg0) throws FtpException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public FtpletResult onConnect(FtpSession arg0) throws FtpException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FtpletResult onDisconnect(FtpSession arg0) throws FtpException, IOException {
		// TODO Auto-generated method stub
		return null;
	}

}
