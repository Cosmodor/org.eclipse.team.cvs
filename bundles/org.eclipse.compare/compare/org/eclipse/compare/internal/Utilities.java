/*
 * Copyright (c) 2000, 2003 IBM Corp.  All rights reserved.
 * This file is made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 */
package org.eclipse.compare.internal;

import java.io.*;
import java.text.MessageFormat;
import java.util.*;

import org.eclipse.swt.widgets.*;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.*;
import org.eclipse.jface.viewers.*;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.ui.*;

import org.eclipse.compare.CompareConfiguration;

/**
 * Convenience and utility methods.
 */
public class Utilities {
	
	public static void registerAction(IKeyBindingService kbs, IAction a, String id) {
		if (kbs != null) {
			a.setActionDefinitionId(id);
			kbs.registerAction(a);
		}
	}
	
	public static IWorkbenchPartSite findSite(Control c) {
		while (c != null && !c.isDisposed()) {
			Object data= c.getData();
			if (data instanceof IWorkbenchPart)
				return ((IWorkbenchPart)data).getSite();
			c= c.getParent();
		}
		return null;
	}

	public static IActionBars findActionBars(Control c) {
		while (c != null && !c.isDisposed()) {
			Object data= c.getData();
			if (data instanceof CompareEditor)
				return ((CompareEditor)data).getActionBars();
				
			// PR 1GDVZV7: ITPVCM:WIN98 - CTRL + C does not work in Java source compare
			if (data instanceof IViewPart)
				return ((IViewPart)data).getViewSite().getActionBars();
			// end PR 1GDVZV7
			
			c= c.getParent();
		}
		return null;
	}

	public static void setEnableComposite(Composite composite, boolean enable) {
		Control[] children= composite.getChildren();
		for (int i= 0; i < children.length; i++)
			children[i].setEnabled(enable);
	}

	public static boolean getBoolean(CompareConfiguration cc, String key, boolean dflt) {
		if (cc != null) {
			Object value= cc.getProperty(key);
			if (value instanceof Boolean)
				return ((Boolean) value).booleanValue();
		}
		return dflt;
	}
	
	public static void firePropertyChange(ListenerList ll, Object source, String property, Object old, Object newValue) {
		if (ll != null) {
			PropertyChangeEvent event= null;
			Object[] listeners= ll.getListeners();
			for (int i= 0; i < listeners.length; i++) {
				IPropertyChangeListener l= (IPropertyChangeListener) listeners[i];
				if (event == null)
					event= new PropertyChangeEvent(source, property, old, newValue);
				l.propertyChange(event);
			}
		}
	}

	public static boolean okToUse(Widget widget) {
		return widget != null && !widget.isDisposed();
	}
	
	public static boolean isMotif() {
		return false;
	}
		
	/**
	 * Returns the elements of the given selection. 
	 * Returns an empty array if the selection is empty or if 
	 * the given selection is not of type <code>IStructuredSelection</code>.
	 *
	 * @param selection the selection
	 * @return the selected elements
	 */
	public static Object[] toArray(ISelection selection) {
		if (!(selection instanceof IStructuredSelection)) {
			return new Object[0];
		}
		IStructuredSelection ss= (IStructuredSelection) selection;
		return ss.toArray();
	}

	/**
	 * Convenience method: extract all <code>IResources</code> from given selection.
	 * Never returns null.
	 */
	public static IResource[] getResources(ISelection selection) {
		
		ArrayList tmp= new ArrayList();

		if (selection instanceof IStructuredSelection) {
		
			Object[] s= ((IStructuredSelection)selection).toArray();
				
			for (int i= 0; i < s.length; i++) {
				Object o= s[i];
				if (o instanceof IResource) {
					tmp.add(o);
					continue;
				}
				if (o instanceof IAdaptable) {
					IAdaptable a= (IAdaptable) o;
					Object adapter= a.getAdapter(IResource.class);
					if (adapter instanceof IResource)
						tmp.add(adapter);
					continue;
				}
			}
		}
		return (IResource[]) tmp.toArray(new IResource[tmp.size()]);
	}

	public static byte[] readBytes(InputStream in) {
		ByteArrayOutputStream bos= new ByteArrayOutputStream();
		try {		
			while (true) {
				int c= in.read();
				if (c == -1)
					break;
				bos.write(c);
			}
					
		} catch (IOException ex) {
			return null;
		
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException x) {
				}
			}
			try {
				bos.close();
			} catch (IOException x) {
			}
		}
		
		return bos.toByteArray();
	}

	/**
	 * Returns null if an error occurred.
	 */
	public static String readString(InputStream is) {
		if (is == null)
			return null;
		BufferedReader reader= null;
		try {
			StringBuffer buffer= new StringBuffer();
			char[] part= new char[2048];
			int read= 0;
			reader= new BufferedReader(new InputStreamReader(is, ResourcesPlugin.getEncoding()));

			while ((read= reader.read(part)) != -1)
				buffer.append(part, 0, read);
			
			return buffer.toString();
			
		} catch (IOException ex) {
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException ex) {
				}
			}
		}
		return null;
	}
	
	public static byte[] getBytes(String s) {
		try {
			return s.getBytes(ResourcesPlugin.getEncoding());
		} catch (UnsupportedEncodingException e) {
			return s.getBytes();
		}
	}

	public static String getIconPath(Display display) {
		return "icons/full/";	//$NON-NLS-1$
	}
	
	/**
	 * Initialize the given Action from a ResourceBundle.
	 */
	public static void initAction(IAction a, ResourceBundle bundle, String prefix) {
		
		String labelKey= "label"; //$NON-NLS-1$
		String tooltipKey= "tooltip"; //$NON-NLS-1$
		String imageKey= "image"; //$NON-NLS-1$
		String descriptionKey= "description"; //$NON-NLS-1$
		
		if (prefix != null && prefix.length() > 0) {
			labelKey= prefix + labelKey;
			tooltipKey= prefix + tooltipKey;
			imageKey= prefix + imageKey;
			descriptionKey= prefix + descriptionKey;
		}
		
		a.setText(getString(bundle, labelKey, labelKey));
		a.setToolTipText(getString(bundle, tooltipKey, null));
		a.setDescription(getString(bundle, descriptionKey, null));
		
		String relPath= getString(bundle, imageKey, null);
		if (relPath != null && relPath.trim().length() > 0) {
			
			String cPath;
			String dPath;
			String ePath;
			
			if (relPath.indexOf("/") >= 0) { //$NON-NLS-1$
				String path= relPath.substring(1);
				cPath= 'c' + path;
				dPath= 'd' + path;
				ePath= 'e' + path;
			} else {
				cPath= "clcl16/" + relPath; //$NON-NLS-1$
				dPath= "dlcl16/" + relPath; //$NON-NLS-1$
				ePath= "elcl16/" + relPath; //$NON-NLS-1$
			}
			
			ImageDescriptor id= CompareUIPlugin.getImageDescriptor(dPath);	// we set the disabled image first (see PR 1GDDE87)
			if (id != null)
				a.setDisabledImageDescriptor(id);
			id= CompareUIPlugin.getImageDescriptor(cPath);
			if (id != null)
				a.setHoverImageDescriptor(id);
			id= CompareUIPlugin.getImageDescriptor(ePath);
			if (id != null)
				a.setImageDescriptor(id);
		}
	}
	
	public static void initToggleAction(IAction a, ResourceBundle bundle, String prefix, boolean checked) {

		String tooltip= null;
		if (checked)
			tooltip= getString(bundle, prefix + "tooltip.checked", null);	//$NON-NLS-1$
		else
			tooltip= getString(bundle, prefix + "tooltip.unchecked", null);	//$NON-NLS-1$
		if (tooltip == null)
			tooltip= getString(bundle, prefix + "tooltip", null);	//$NON-NLS-1$
		
		if (tooltip != null)
			a.setToolTipText(tooltip);
			
		String description= null;
		if (checked)
			description= getString(bundle, prefix + "description.checked", null);	//$NON-NLS-1$
		else
			description= getString(bundle, prefix + "description.unchecked", null);	//$NON-NLS-1$
		if (description == null)
			description= getString(bundle, prefix + "description", null);	//$NON-NLS-1$
		
		if (description != null)
			a.setDescription(description);
			
	}

	public static String getString(ResourceBundle bundle, String key, String dfltValue) {
		
		if (bundle != null) {
			try {
				return bundle.getString(key);
			} catch (MissingResourceException x) {
			}
		}
		return dfltValue;
	}
	
	public static String getFormattedString(ResourceBundle bundle, String key, String arg) {
		
		if (bundle != null) {
			try {
				return MessageFormat.format(bundle.getString(key), new String[] { arg });
			} catch (MissingResourceException x) {
			}
		}
		return "!" + key + "!";//$NON-NLS-2$ //$NON-NLS-1$
	}
	
	public static String getString(String key) {
		try {
			return CompareUIPlugin.getResourceBundle().getString(key);
		} catch (MissingResourceException e) {
			return "!" + key + "!";//$NON-NLS-2$ //$NON-NLS-1$
		}
	}
	
	public static String getFormattedString(String key, String arg) {
		try{
			return MessageFormat.format(CompareUIPlugin.getResourceBundle().getString(key), new String[] { arg });
		} catch (MissingResourceException e) {
			return "!" + key + "!";//$NON-NLS-2$ //$NON-NLS-1$
		}	
	}

	public static String getString(ResourceBundle bundle, String key) {
		return getString(bundle, key, key);
	}
	
	public static int getInteger(ResourceBundle bundle, String key, int dfltValue) {
		
		if (bundle != null) {
			try {
				String s= bundle.getString(key);
				if (s != null)
					return Integer.parseInt(s);
			} catch (NumberFormatException x) {
			} catch (MissingResourceException x) {
			}
		}
		return dfltValue;
	}

	/**
	 * Answers <code>true</code> if the given selection contains resources that don't
	 * have overlapping paths and <code>false</code> otherwise. 
	 */
	/*
	public static boolean isSelectionNonOverlapping() throws TeamException {
		IResource[] resources = getSelectedResources();
		// allow operation for non-overlapping resource selections
		if(resources.length>0) {
			List validPaths = new ArrayList(2);
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				
				// only allow cvs resources to be selected
				if(RepositoryProvider.getProvider(resource.getProject(), CVSProviderPlugin.getTypeId()) == null) {
					return false;
				}
				
				// check if this resource overlaps other selections		
				IPath resourceFullPath = resource.getFullPath();
				if(!validPaths.isEmpty()) {
					for (Iterator it = validPaths.iterator(); it.hasNext();) {
						IPath path = (IPath) it.next();
						if(path.isPrefixOf(resourceFullPath) || 
					       resourceFullPath.isPrefixOf(path)) {
							return false;
						}
					}
				}
				validPaths.add(resourceFullPath);
				
				// ensure that resources are managed
				ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
				if(cvsResource.isFolder()) {
					if( ! ((ICVSFolder)cvsResource).isCVSFolder()) return false;
				} else {
					if( ! cvsResource.isManaged()) return false;
				}
			}
			return true;
		}
		return false;
	}
	*/
}
