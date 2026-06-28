package com.reForm.backend.auth.service;

import com.reForm.backend.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;


//spring needs to know what is A USER is in our app
//spring has an interface for us to do exactly that, which is UserDetails
//there will be a customerDetailService, that needs to do its job, it will get the User,
//you then make a new CustomerUserDetail object to wrap this User, thats why we need the constructor
public class CustomerUserDetails implements UserDetails {
    private final User user;

    public CustomerUserDetails(User user) {
        this.user = user;
    }


    //tells spring security the roles this user has through list of SimpleGrantedAuthority
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = "ROLE_" + user.getRole().name();
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }


    //so here I should use email, since I index email, it is unique,
    //email is already unique, which is better
    @Override
    public String getUsername() {
        return user.getEmail();
    }


    //after done with login, user is placed in security context holder
    //this allow the app to take the id from thread memory, no need to query database
    public UUID getId() {
        return user.getId();
    }

    /*
    the methods under is for when our app has the capability to
    lock suspended accounts, expire old passwords, or temporarily disable users.
    right now, there are no columns for this in the database, so just return true;
    but when you click in the UserDetail class, you see the interface already return true
    so you dont really need to implement them since our have dont ban user yet
     */

//    @Override
//    public boolean isAccountNonExpired() {
//        return UserDetails.super.isAccountNonExpired();
//    }
//
//    @Override
//    public boolean isAccountNonLocked() {
//        return UserDetails.super.isAccountNonLocked();
//    }
//
//    @Override
//    public boolean isCredentialsNonExpired() {
//        return UserDetails.super.isCredentialsNonExpired();
//    }
//
//    @Override
//    public boolean isEnabled() {
//        return UserDetails.super.isEnabled();
//    }
}
