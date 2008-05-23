/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of the Darkstar Test Cluster
 *
 * Darkstar Test Cluster is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Darkstar Test Cluster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.qa.tc.domain;

import java.util.SortedSet;
import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Id;
import javax.persistence.GeneratedValue;
import javax.persistence.Column;
import javax.persistence.Enumerated;
import javax.persistence.EnumType;
import javax.persistence.ManyToOne;
import javax.persistence.ManyToMany;
import javax.persistence.JoinTable;
import javax.persistence.JoinColumn;

/**
 *
 * @author owen
 */
@Entity
@Table(name = "ClientAppConfig")
public class ClientAppConfig implements Serializable
{
    public static enum Type {PROPERTIES, CLI, ENV};
    
    private Long id;
    private String name;
    private String path;
    private Type propertyMethod;
    
    private ClientApp clientApp;
    private SortedSet<Property> properties;
    
    public ClientAppConfig(String name,
                           String path,
                           Type propertyMethod)
    {
        this.setName(name);
        this.setPath(path);
        this.setPropertyMethod(propertyMethod);
    }
    
    @Id
    @GeneratedValue
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    @Column(name = "name", nullable = false)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    @Column(name = "path", nullable = false)
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    
    @Column(name = "propertyMethod", nullable = false)
    @Enumerated(EnumType.STRING)
    public Type getPropertyMethod() { return propertyMethod; }
    public void setPropertyMethod(Type propertyMethod) { this.propertyMethod = propertyMethod; }
    
    @ManyToOne
    @JoinColumn(name="clientApp")
    public ClientApp getClientApp() { return clientApp; }
    public void setClientApp(ClientApp clientApp) { this.clientApp = clientApp; }
    
    @ManyToMany
    @JoinTable(name = "clientAppConfigProperties",
               joinColumns = @JoinColumn(name = "clientAppConfigId"),
               inverseJoinColumns = @JoinColumn(name = "propertyId"))
    public SortedSet<Property> getProperties() { return properties; }
    public void setProperties(SortedSet<Property> properties) { this.properties = properties; }
}
