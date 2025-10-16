package com.papeleria.pos.services;

import com.papeleria.pos.models.Role;
import com.papeleria.pos.models.User;

import java.util.*;
import java.util.stream.Collectors;

public class UserService {
    private final StorageService storage;

    public UserService(StorageService storage) {
        this.storage = storage;
        seedDefaults();
    }

    public List<User> list() {
        return new ArrayList<>(storage.loadUsers());
    }

    public Optional<User> find(String username) {
        return list().stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst();
    }

    public void upsert(User u) {
        List<User> all = list();
        Optional<User> ex = all.stream().filter(x -> x.getUsername().equalsIgnoreCase(u.getUsername())).findFirst();
        if (ex.isPresent()) {
            ex.get().setPassword(u.getPassword());
            ex.get().setRole(u.getRole());
        } else {
            all.add(u);
        }
        storage.saveUsers(all);
    }

    public boolean remove(String username) {
        List<User> all = list();
        Optional<User> ex = all.stream().filter(u -> u.getUsername().equalsIgnoreCase(username)).findFirst();
        if (ex.isEmpty()) return false;

        // No permitir dejar el sistema sin ADMIN
        long admins = all.stream().filter(u -> u.getRole() == Role.ADMIN).count();
        if (ex.get().getRole() == Role.ADMIN && admins <= 1) return false;

        all = all.stream().filter(u -> !u.getUsername().equalsIgnoreCase(username)).collect(Collectors.toList());
        storage.saveUsers(all);
        return true;
    }

    public boolean validate(String username, String password) {
        return list().stream().anyMatch(u -> u.getUsername().equals(username) && u.getPassword().equals(password));
    }

    public Role roleOf(String username) {
        return find(username).map(User::getRole).orElse(Role.SELLER);
    }

    private void seedDefaults() {
        List<User> users = storage.loadUsers();
        if (users == null || users.isEmpty()) {
            users = new ArrayList<>();
            users.add(new User("admin", "admin", Role.ADMIN));
            users.add(new User("vendedor", "1234", Role.SELLER));
            storage.saveUsers(users);
        }
    }
}
