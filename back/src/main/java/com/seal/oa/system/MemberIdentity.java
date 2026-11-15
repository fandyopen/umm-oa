package com.seal.oa.system;

/** Member directories must not include system administrator identities. Role-based managers remain members. */
public final class MemberIdentity {
    private MemberIdentity() {}

    /** The alias is an internal SQL identifier, never request input. */
    public static String condition(String alias) {
        if (!alias.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("Invalid SQL alias");
        return alias + ".user_id<>1 AND NOT EXISTS (SELECT 1 FROM sys_admin_identity member_admin"
                + " WHERE member_admin.user_name=" + alias + ".user_name)";
    }
}
