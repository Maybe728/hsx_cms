package com.cn.hsx.tyqxcms.config;

import com.alibaba.dubbo.config.annotation.Reference;
import com.cn.hsx.tyqxcms.dto.UserInfo;
import com.cn.hsx.tyqxcms.entity.LoginLog;
import com.cn.hsx.tyqxcms.entity.Permission;
import com.cn.hsx.tyqxcms.service.ILoginLogService;
import com.cn.hsx.tyqxcms.service.IUserService;
import com.cn.hsx.tyqxcms.util.AddressUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.ByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShiroRealm extends AuthorizingRealm {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Reference(version = "1.0.0")
    private IUserService iUserService;

    @Reference(version = "1.0.0")
    private ILoginLogService iloginLogService;

    /*主要是用来进行权限认证的，也就是说验证用户具有哪些权限*/
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {

        logger.info("权限配置----->ShiroRealm.doGetAuthorizationInfo()");

        SimpleAuthorizationInfo authorizationInfo = new SimpleAuthorizationInfo();
        UserInfo userInfo  = (UserInfo)principals.getPrimaryPrincipal();
        authorizationInfo.addRole(userInfo.getRoleInfo().getRoleCode());
        for(Permission p:userInfo.getRoleInfo().getPermissions()){
            authorizationInfo.addStringPermission(p.getPermissionCode());
        }
        System.out.println("asd");
        return authorizationInfo;
    }

    /*主要是用来进行身份认证的，也就是说验证用户输入的账号和密码是否正确*/
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {

        logger.info("ShiroRealm.doGetAuthenticationInfo()");

        //获取用户的输入的账号.
        String username = (String)token.getPrincipal();
        //通过username从数据库中查找 User对象，如果找到，没找到.
        //实际项目中，这里可以根据实际情况做缓存，如果不做，Shiro自己也是有时间间隔机制，2分钟内不会重复执行该方法
        UserInfo userInfo = iUserService.findUserInfo(username);

        logger.info("----->userInfo=" + userInfo);
        //获取用户信息，是否存在该用户，该用户是否被删除，该用户是否被禁用
        if(userInfo == null){
            throw new AccountException();
        }else if(userInfo.getState() == 0){
            throw new DisabledAccountException();
        }else if(userInfo.getState() == 2){
            throw new LockedAccountException();
        }
        //保存登录用户ID
        Session session = SecurityUtils.getSubject().getSession();
        session.setAttribute("loginUserId",userInfo.getId());

        SimpleAuthenticationInfo authenticationInfo = new SimpleAuthenticationInfo(
                userInfo, //用户信息
                userInfo.getPassWord(), //密码
                ByteSource.Util.bytes(userInfo.getCredentialsSalt()),//salt=username+salt
                getName()  //realm name
        );

//        认证成功添加登录日志
        addLoginLog(userInfo, session);

        return authenticationInfo;
    }

    private void addLoginLog(UserInfo userInfo, Session session){
        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(userInfo.getId());
        loginLog.setUserName(userInfo.getUserName());
        loginLog.setIpAddress(AddressUtils.INTERNET_IP);
        loginLog.setGeographyLocation("局域网测试");
        iloginLogService.insert(loginLog);
    }

}