package org.andresoviedo.google_drive_ftp_adapter.view.ftp;

import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.AuthorizationRequest;

public final class Authorities {

    public static class PWDPermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class CWDPermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class ListPermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class DeletePermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class RetrievePermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class RemoveDirPermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class MakeDirPermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class AppendPermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class StorePermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }

    public static class RenameToPermission implements Authority {

        @Override
        public AuthorizationRequest authorize(AuthorizationRequest arg0) {
            return null;
        }

        @Override
        public boolean canAuthorize(AuthorizationRequest arg0) {
            return false;
        }
    }
}
