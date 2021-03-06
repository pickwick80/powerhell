/**
 * Copyright (c) 2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.powerhell;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.net.ssl.HostnameVerifier;
import javax.xml.bind.DatatypeConverter;

import org.apache.cxf.interceptor.Fault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.cloudsoft.winrm4j.client.Command;
import io.cloudsoft.winrm4j.client.WinRmClient;

/**
 * <p>
 * TODO
 * </p> 
 * 
 * @author semancik
 */
public abstract class AbstractPowerHellImpl implements PowerHell {
	
	private static final Logger LOG = LoggerFactory.getLogger(AbstractPowerHellImpl.class);

	private static final String POWERSHELL_COMMAND = "powershell";
	private static final String POWERSHELL_ENCODED_COMMAND_PARAM = "-EncodedCommand";
	
	private ArgumentStyle argumentStyle;
		
	public ArgumentStyle getArgumentStyle() {
		return argumentStyle;
	}

	public void setArgumentStyle(ArgumentStyle argumentStyle) {
		this.argumentStyle = argumentStyle;
	}

	protected void processFault(String message, Fault e) throws PowerHellSecurityException, PowerHellCommunicationException {
		// Fault does not have useful information on its own. Try to mine out something useful.
		Throwable cause = e.getCause();
		if (cause instanceof IOException) {
			if (cause.getMessage() != null && cause.getMessage().contains("Authorization loop detected")) {
				throw new PowerHellSecurityException(cause.getMessage(), e);
			}
		}
		throw new PowerHellCommunicationException(message + ": " + e.getMessage(), e);
	}
		
	protected String encodeUtf16Base64(String command) {
		byte[] bytes = command.getBytes(Charset.forName("UTF-16LE"));
        return DatatypeConverter.printBase64Binary(bytes);
	}
	
	protected String encodePowerShellToString(String command, Map<String,Object> arguments) {
		String psScript = createPowerShellScripWithArguments(command, arguments);
		return POWERSHELL_COMMAND + " " + POWERSHELL_ENCODED_COMMAND_PARAM + " " + encodeUtf16Base64(psScript);
	}
	
	protected List<String> encodePowerShellToList(String command, Map<String,Object> arguments) {
		List<String> commandLine = new ArrayList<>();
		commandLine.add(POWERSHELL_COMMAND);
		if (arguments == null) {
			return commandLine;
		}
		String psScript = createPowerShellScripWithArguments(command, arguments);
		commandLine.add(POWERSHELL_ENCODED_COMMAND_PARAM);
		commandLine.add(encodeUtf16Base64(psScript));
		return commandLine;
	}
	
	/**
	 * Creates:
	 * The-Ugly-PowerShell-Command -Arg1 val1
	 * ... or ...
	 * $Arg1 = val1; The-Ugly-PowerShell-Command
	 */
	protected String createPowerShellScripWithArguments(String command, Map<String,Object> arguments) {
		if (arguments == null) {
			return command;
		} else if (getArgumentStyle() == ArgumentStyle.VARIABLES) {
			return encodePowerShellVariablesAndCommandToString(command, arguments);
		} else {
			return encodeCommandExecToString(command, arguments);
		}
	}
	
	protected String encodePowerShellVariablesAndCommandToString(String command, Map<String, Object> arguments) {
		if (arguments == null) {
			return command;
		}
		StringBuilder commandLineBuilder = new StringBuilder();
		String paramPrefix = getParamPrefix();
		for (Entry<String, Object> argEntry: arguments.entrySet()) {
			commandLineBuilder.append(paramPrefix).append(argEntry.getKey());
			commandLineBuilder.append(" = ");
			commandLineBuilder.append(quoteSingle(argEntry.getValue().toString()));
			commandLineBuilder.append("; ");
		}
		commandLineBuilder.append(command);
		return commandLineBuilder.toString();
	}
	
	protected List<String> encodeCommandExecToList(String command, Map<String,Object> arguments) {
		List<String> commandLine = new ArrayList<>();
		commandLine.add(command);
		if (arguments == null) {
			return commandLine;
		}
		String paramPrefix = getParamPrefix();
		for (Entry<String, Object> argEntry: arguments.entrySet()) {
			commandLine.add(paramPrefix + argEntry.getKey());
			if (argEntry.getValue() != null) {
				commandLine.add(argEntry.getValue().toString());
			}
		}
		return commandLine;
	}
	
	protected String encodeCommandExecToString(String command, Map<String,Object> arguments) {
		if (arguments == null) {
			return command;
		}
		StringBuilder commandLineBuilder = new StringBuilder();
		commandLineBuilder.append(command);
		String paramPrefix = getParamPrefix();
		for (Entry<String, Object> argEntry: arguments.entrySet()) {
			commandLineBuilder.append(" ");
			commandLineBuilder.append(paramPrefix).append(argEntry.getKey());
			if (argEntry.getValue() != null) {
				commandLineBuilder.append(" ");
				commandLineBuilder.append(argEntry.getValue().toString());
			}
		}
		return commandLineBuilder.toString();
	}

	protected String quoteSingle(Object value) {
		if (value == null) {
			return "";
		}
		return "'" + value.toString().replaceAll("'", "''") + "'";
	}
	
	protected String getParamPrefix() {
		if (getArgumentStyle() == null) {
			return ArgumentStyle.PARAMETERS_DASH.getPrefix();
		}
		return getArgumentStyle().getPrefix();
	}

	
	protected void logData(String prefix, String data) {
		if (LOG.isTraceEnabled()) {
			if (data != null && !data.isEmpty()) {
				LOG.trace("{} {}", prefix, data);
			}
		}
	}
	
	protected void logExecution(String outCommandLine, long tsCommStart) {
		long tsCommStop = System.currentTimeMillis();
		LOG.debug("Command {} run time: {} ms", outCommandLine, tsCommStop-tsCommStart);
	}

}
