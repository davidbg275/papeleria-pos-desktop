package com.papeleria.pos.services;

import com.papeleria.pos.models.Role;
import com.papeleria.pos.models.User;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SessionService {
    private final StorageService storage;
    private String username = "";
    private Role role = Role.SELLER;

    public SessionService(StorageService storage){
        this.storage = storage;
        Map<String, String> s = storage.loadSession();
        this.username = s.getOrDefault("username", "");
        this.role = Role.fromString(s.getOrDefault("role", "SELLER"));
    }

    public boolean login(String user, String pass){
        List<User> users = storage.loadUsers();
        Optional<User> ok = users.stream()
                .filter(u -> u.getUsername().equals(user) && u.getPassword().equals(pass))
                .findFirst();
        if (ok.isPresent()){
            this.username = ok.get().getUsername();
            this.role = ok.get().getRole();
            storage.saveSession(username, role.name());
            return true;
        }
        return false;
    }

    public void logout(){
        this.username = "";
        this.role = Role.SELLER;
        storage.saveSession("", "");
    }

    public String getUsername(){ return username; }
    public Role getRole(){ return role; }

    public boolean isAdmin(){ return role == Role.ADMIN; }
}
