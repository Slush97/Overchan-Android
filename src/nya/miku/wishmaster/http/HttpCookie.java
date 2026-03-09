/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nya.miku.wishmaster.http;

import java.util.Date;

public class HttpCookie {
    private final String name;
    private String value;
    private String domain;
    private String path;
    private Date expiryDate;

    public HttpCookie(String name, String value) {
        this.name = name;
        this.value = value;
        this.path = "/";
    }

    public String getName() { return name; }
    public String getValue() { return value; }
    public String getDomain() { return domain; }
    public String getPath() { return path; }
    public Date getExpiryDate() { return expiryDate; }

    public void setValue(String value) { this.value = value; }
    public void setDomain(String domain) { this.domain = domain; }
    public void setPath(String path) { this.path = path; }
    public void setExpiryDate(Date expiryDate) { this.expiryDate = expiryDate; }

    public boolean isExpired(Date date) {
        return expiryDate != null && expiryDate.before(date);
    }

    @Override
    public String toString() {
        return name + "=" + value + "; domain=" + domain + "; path=" + path;
    }
}
