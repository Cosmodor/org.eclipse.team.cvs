/*******************************************************************************
 * Copyright (c) 2006, 2017 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.core;

import org.eclipse.core.resources.mapping.ModelProvider;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.team.internal.core.mapping.ModelProviderResourceMapping;

public class AdapterFactory implements IAdapterFactory {

	@Override
	@SuppressWarnings("unchecked")
	public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
		if (adaptableObject instanceof ModelProvider && adapterType == ResourceMapping.class) {
			ModelProvider mp = (ModelProvider) adaptableObject;
			return (T) new ModelProviderResourceMapping(mp);
		}
		return null;
	}

	@Override
	public Class<?>[] getAdapterList() {
		return new Class[] { ResourceMapping.class};
	}

}
