// Copyright 2015 Eivind Vegsundvåg
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package ninja.eivind.hotsreplayuploader.models.stringconverters;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import javax.inject.Singleton;

/**
 * Binds a {@link StringProperty} representing the current application status to various elements, including the
 * {@link java.awt.SystemTray} icon and the {@link ninja.eivind.hotsreplayuploader.window.HomeController}'s status
 * display.
 */
@Singleton
public class StatusBinder {

    private final StringProperty message = new SimpleStringProperty();

    public StringProperty message() {
        return message;
    }
}
